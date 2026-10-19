package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.progress.service.PotdCompletionService;
import com.hrishabh.algocracksubmissionservice.progress.service.ProgressOutboxService;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsOutboxService;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsUpdateProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * Short transactions for submission state. The verdict, progress outbox row, and
 * statistics outbox row commit together. WebSocket notification, statistics application,
 * and the immediate daily-challenge write run only after that commit.
 */
@Slf4j
@Service
public class SubmissionFinalizer {

    private final SubmissionRepository submissionRepository;
    private final ProgressOutboxService progressOutboxService;
    private final PotdCompletionService potdCompletionService;
    private final QuestionStatisticsOutboxService statisticsOutboxService;
    private final QuestionStatisticsUpdateProcessor statisticsUpdateProcessor;
    private final WebSocketService webSocketService;
    private final boolean dispatchStatisticsOnCommit;

    public SubmissionFinalizer(
            SubmissionRepository submissionRepository,
            ProgressOutboxService progressOutboxService,
            PotdCompletionService potdCompletionService,
            QuestionStatisticsOutboxService statisticsOutboxService,
            QuestionStatisticsUpdateProcessor statisticsUpdateProcessor,
            WebSocketService webSocketService,
            @Value("${statistics.dispatch-on-commit:true}") boolean dispatchStatisticsOnCommit) {
        this.submissionRepository = submissionRepository;
        this.progressOutboxService = progressOutboxService;
        this.potdCompletionService = potdCompletionService;
        this.statisticsOutboxService = statisticsOutboxService;
        this.statisticsUpdateProcessor = statisticsUpdateProcessor;
        this.webSocketService = webSocketService;
        this.dispatchStatisticsOnCommit = dispatchStatisticsOnCommit;
    }

    public static boolean isTerminalStatus(SubmissionStatus status) {
        return status == SubmissionStatus.COMPLETED
                || status == SubmissionStatus.FAILED
                || status == SubmissionStatus.CANCELLED;
    }

    @Transactional(readOnly = true)
    public SubmissionSnapshot loadSnapshot(String submissionId) {
        Submission submission = submissionRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));
        return new SubmissionSnapshot(
                submission.getSubmissionId(),
                submission.getUserId(),
                submission.getQuestionId(),
                submission.getLanguage(),
                submission.getCode(),
                submission.getStatus());
    }

    @Transactional(readOnly = true)
    public boolean isTerminal(String submissionId) {
        return submissionRepository.findBySubmissionId(submissionId)
                .map(submission -> isTerminalStatus(submission.getStatus()))
                .orElse(false);
    }

    @Transactional
    public void markInProgress(String submissionId, SubmissionStatus target) {
        if (target != SubmissionStatus.COMPILING && target != SubmissionStatus.RUNNING) {
            throw new IllegalArgumentException("Not an in-progress status: " + target);
        }
        Submission submission = lockSubmission(submissionId);
        if (!canTransition(submission.getStatus(), target)) {
            log.debug("Ignoring status {} for submission {} currently {}",
                    target, submissionId, submission.getStatus());
            return;
        }
        submission.setStatus(target);
        if (target == SubmissionStatus.COMPILING && submission.getStartedAt() == null) {
            submission.setStartedAt(LocalDateTime.now());
        }
        submissionRepository.save(submission);
        afterCommit(() -> webSocketService.sendStatus(submission));
    }

    /**
     * @return true when this call persisted the terminal verdict
     */
    @Transactional
    public boolean finalizeSubmission(String submissionId, SubmissionCompletion completion) {
        Submission submission = lockSubmission(submissionId);
        if (isTerminalStatus(submission.getStatus())) {
            log.info("Submission {} already {}; skipping duplicate finalization",
                    submissionId, submission.getStatus());
            return false;
        }
        if (!canTransition(submission.getStatus(), SubmissionStatus.COMPLETED)) {
            log.warn("Refusing to complete submission {} from status {}", submissionId, submission.getStatus());
            return false;
        }

        SubmissionVerdict verdict = completion.verdict();
        if (verdict == SubmissionVerdict.ACCEPTED && !completion.executionSucceeded()) {
            log.error("Refusing ACCEPTED for unsuccessful execution of submission {}", submissionId);
            verdict = SubmissionVerdict.INTERNAL_ERROR;
        }

        submission.setStatus(SubmissionStatus.COMPLETED);
        submission.setVerdict(verdict);
        submission.setRuntimeMs(completion.runtimeMs());
        submission.setMemoryKb(completion.memoryKb());
        submission.setCompletedAt(LocalDateTime.now());
        submission.setWorkerId(completion.workerId());
        submission.setTestResults(completion.testResults() != null ? completion.testResults() : "[]");
        submission.setCompilationOutput(completion.compilationOutput());
        submission.setErrorMessage(completion.errorMessage());
        submissionRepository.save(submission);

        progressOutboxService.enqueueAcceptedSubmission(submission);
        long statisticsOutboxId = statisticsOutboxService.enqueue(submission.getSubmissionId(), submission.getQuestionId());

        SubmissionVerdict publishedVerdict = verdict;
        afterCommit(() -> webSocketService.sendResult(submission));
        if (dispatchStatisticsOnCommit) {
            afterCommit(() -> statisticsUpdateProcessor.processOne(statisticsOutboxId));
        }
        if (publishedVerdict == SubmissionVerdict.ACCEPTED) {
            afterCommit(() -> potdCompletionService.recordAcceptedSubmission(submission));
        }

        log.info("Submission {} completed with verdict: {}", submissionId, publishedVerdict);
        return true;
    }

    @Transactional
    public void markFailed(String submissionId, String errorMessage) {
        Submission submission = lockSubmission(submissionId);
        if (!canTransition(submission.getStatus(), SubmissionStatus.FAILED)) {
            log.info("Not marking submission {} failed; status is {}", submissionId, submission.getStatus());
            return;
        }
        submission.setStatus(SubmissionStatus.FAILED);
        submission.setErrorMessage(errorMessage);
        submission.setCompletedAt(LocalDateTime.now());
        submissionRepository.save(submission);
        afterCommit(() -> webSocketService.sendError(submission, errorMessage));
    }

    private Submission lockSubmission(String submissionId) {
        return submissionRepository.findBySubmissionIdForUpdate(submissionId)
                .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionId));
    }

    private static boolean canTransition(SubmissionStatus from, SubmissionStatus to) {
        if (from == to || isTerminalStatus(from)) {
            return false;
        }
        return switch (to) {
            case COMPILING -> from == SubmissionStatus.QUEUED;
            case RUNNING -> from == SubmissionStatus.QUEUED || from == SubmissionStatus.COMPILING;
            case COMPLETED, FAILED -> from == SubmissionStatus.QUEUED
                    || from == SubmissionStatus.COMPILING
                    || from == SubmissionStatus.RUNNING;
            default -> false;
        };
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Submission events require an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException ex) {
                    log.error("Post-commit submission action failed: {}", ex.getMessage(), ex);
                }
            }
        });
    }
}
