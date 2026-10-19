package com.hrishabh.algocracksubmissionservice.statistics;

import com.hrishabh.algocracksubmissionservice.models.QuestionStatistics;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.repository.QuestionStatisticsRepository;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.statistics.model.QuestionStatisticsOutbox;
import com.hrishabh.algocracksubmissionservice.statistics.model.StatisticsOutboxStatus;
import com.hrishabh.algocracksubmissionservice.statistics.repository.QuestionStatisticsOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Applies one statistics outbox row. Each call runs in its own transaction so an
 * optimistic-lock failure rolls back only that attempt and the next attempt reloads
 * the row. This bean is separate from the retry loop to avoid @Transactional self-invocation.
 */
@Slf4j
@Service
public class QuestionStatisticsApplier {

    private final QuestionStatisticsOutboxRepository outboxRepository;
    private final QuestionStatisticsRepository statisticsRepository;
    private final SubmissionRepository submissionRepository;
    private final ObjectProvider<StatisticsVersionConflictProbe> conflictProbe;
    private final Clock clock;

    public QuestionStatisticsApplier(
            QuestionStatisticsOutboxRepository outboxRepository,
            QuestionStatisticsRepository statisticsRepository,
            SubmissionRepository submissionRepository,
            ObjectProvider<StatisticsVersionConflictProbe> conflictProbe,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.statisticsRepository = statisticsRepository;
        this.submissionRepository = submissionRepository;
        this.conflictProbe = conflictProbe;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apply(long outboxId) {
        QuestionStatisticsOutbox event = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (event == null || event.getStatus() != StatisticsOutboxStatus.PENDING) {
            return;
        }
        Submission submission = submissionRepository.findBySubmissionId(event.getSubmissionId()).orElse(null);
        if (submission == null
                || submission.getStatus() != SubmissionStatus.COMPLETED
                || submission.getVerdict() == null) {
            defer(event, "Submission is not finalized");
            return;
        }

        QuestionStatistics stats = statisticsRepository.findByQuestionId(submission.getQuestionId())
                .orElseGet(() -> QuestionStatistics.builder()
                        .questionId(submission.getQuestionId())
                        .totalSubmissions(0)
                        .acceptedSubmissions(0)
                        .build());
        if (stats.getId() != 0) {
            conflictProbe.ifAvailable(probe -> probe.beforeWrite(stats.getQuestionId()));
        }
        stats.incrementSubmissions(
                submission.getVerdict() == SubmissionVerdict.ACCEPTED,
                submission.getRuntimeMs(),
                submission.getMemoryKb());
        statisticsRepository.saveAndFlush(stats);

        event.setStatus(StatisticsOutboxStatus.PROCESSED);
        event.setLastError(null);
        outboxRepository.save(event);
        log.debug("Applied statistics for submission {} question {} total={} accepted={}",
                submission.getSubmissionId(), submission.getQuestionId(),
                stats.getTotalSubmissions(), stats.getAcceptedSubmissions());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordConflict(long outboxId, RuntimeException failure) {
        QuestionStatisticsOutbox event = outboxRepository.findById(outboxId).orElse(null);
        if (event == null || event.getStatus() != StatisticsOutboxStatus.PENDING) {
            return;
        }
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(truncate(failure == null
                ? "statistics update conflict"
                : failure.getClass().getSimpleName() + ": " + failure.getMessage()));
        long delaySeconds = Math.min(300L, 5L * event.getAttempts());
        event.setNextAttemptAt(LocalDateTime.now(clock).plusSeconds(delaySeconds));
        outboxRepository.save(event);
        log.error("Question statistics update deferred for submission {} outbox {} after retries: {}",
                event.getSubmissionId(), outboxId, event.getLastError());
    }

    private void defer(QuestionStatisticsOutbox event, String reason) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(truncate(reason));
        long delaySeconds = Math.min(300L, 5L * Math.max(event.getAttempts(), 1));
        event.setNextAttemptAt(LocalDateTime.now(clock).plusSeconds(delaySeconds));
        log.warn("Deferring statistics for submission {}: {}", event.getSubmissionId(), reason);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
