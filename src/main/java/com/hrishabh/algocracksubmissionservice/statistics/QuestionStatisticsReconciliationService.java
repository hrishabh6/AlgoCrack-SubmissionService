package com.hrishabh.algocracksubmissionservice.statistics;

import com.hrishabh.algocracksubmissionservice.models.QuestionStatistics;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.repository.QuestionStatisticsRepository;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.statistics.model.QuestionStatisticsOutbox;
import com.hrishabh.algocracksubmissionservice.statistics.model.StatisticsOutboxStatus;
import com.hrishabh.algocracksubmissionservice.statistics.repository.QuestionStatisticsOutboxRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rebuilds question statistics from completed submissions and retires the matching
 * outbox rows in the same transaction, so replay cannot double-count them.
 */
@Service
public class QuestionStatisticsReconciliationService {

    private final QuestionStatisticsReconcileTransaction reconcileTransaction;

    public QuestionStatisticsReconciliationService(QuestionStatisticsReconcileTransaction reconcileTransaction) {
        this.reconcileTransaction = reconcileTransaction;
    }

    public QuestionStatistics reconcile(Long questionId) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= QuestionStatisticsUpdateProcessor.MAX_ATTEMPTS; attempt++) {
            try {
                return reconcileTransaction.reconcile(questionId);
            } catch (OptimisticLockingFailureException
                    | PessimisticLockingFailureException
                    | DataIntegrityViolationException ex) {
                last = ex;
            }
        }
        throw new StatisticsReconciliationException(
                "Question statistics reconciliation exhausted retries for question " + questionId, last);
    }

    @Service
    public static class QuestionStatisticsReconcileTransaction {

        private final SubmissionRepository submissionRepository;
        private final QuestionStatisticsRepository statisticsRepository;
        private final QuestionStatisticsOutboxRepository outboxRepository;

        private final ObjectProvider<StatisticsReconcileGap> reconcileGap;

        public QuestionStatisticsReconcileTransaction(
                SubmissionRepository submissionRepository,
                QuestionStatisticsRepository statisticsRepository,
                QuestionStatisticsOutboxRepository outboxRepository,
                ObjectProvider<StatisticsReconcileGap> reconcileGap) {
            this.submissionRepository = submissionRepository;
            this.statisticsRepository = statisticsRepository;
            this.outboxRepository = outboxRepository;
            this.reconcileGap = reconcileGap;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public QuestionStatistics reconcile(Long questionId) {
            // A submission can be finalized and applied after an unlocked snapshot.
            // Lock the statistics row first, then reread completed submissions, so that
            // absolute rewrite cannot drop a row whose outbox apply already committed
            // and cannot double-count one that is still pending.
            reconcileGap.ifAvailable(gap -> gap.beforeLockedRead(questionId));
            QuestionStatistics stats = lockStatistics(questionId);
            List<Submission> completed = submissionRepository.findCompletedByQuestionIdForUpdate(questionId);
            List<QuestionStatisticsOutbox> pending = outboxRepository.findByQuestionIdAndStatusForUpdate(
                    questionId, StatisticsOutboxStatus.PENDING);

            int accepted = 0;
            Integer bestRuntime = null;
            Integer bestMemory = null;
            LocalDateTime lastSubmissionAt = null;
            Set<String> completedIds = new HashSet<>();
            for (Submission submission : completed) {
                completedIds.add(submission.getSubmissionId());
                if (submission.getCompletedAt() != null
                        && (lastSubmissionAt == null || submission.getCompletedAt().isAfter(lastSubmissionAt))) {
                    lastSubmissionAt = submission.getCompletedAt();
                }
                if (submission.getVerdict() == SubmissionVerdict.ACCEPTED) {
                    accepted++;
                    if (submission.getRuntimeMs() != null
                            && (bestRuntime == null || submission.getRuntimeMs() < bestRuntime)) {
                        bestRuntime = submission.getRuntimeMs();
                    }
                    if (submission.getMemoryKb() != null
                            && (bestMemory == null || submission.getMemoryKb() < bestMemory)) {
                        bestMemory = submission.getMemoryKb();
                    }
                }
            }

            stats.setTotalSubmissions(completed.size());
            stats.setAcceptedSubmissions(accepted);
            stats.setBestRuntimeMs(bestRuntime);
            stats.setBestMemoryKb(bestMemory);
            stats.setLastSubmissionAt(lastSubmissionAt);
            statisticsRepository.saveAndFlush(stats);

            for (QuestionStatisticsOutbox row : pending) {
                if (completedIds.contains(row.getSubmissionId())) {
                    row.setStatus(StatisticsOutboxStatus.PROCESSED);
                    row.setLastError(null);
                }
            }
            outboxRepository.saveAll(pending);
            return stats;
        }

        private QuestionStatistics lockStatistics(Long questionId) {
            return statisticsRepository.findByQuestionIdForUpdate(questionId)
                    .orElseGet(() -> statisticsRepository.saveAndFlush(QuestionStatistics.builder()
                            .questionId(questionId)
                            .totalSubmissions(0)
                            .acceptedSubmissions(0)
                            .build()));
        }
    }
}
