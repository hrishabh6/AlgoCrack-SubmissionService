package algocrack.submissiontest;

import com.hrishabh.algocracksubmissionservice.adapter.ExecutionAdapter;
import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.judging.PipelineAssembler;
import com.hrishabh.algocracksubmissionservice.progress.service.PotdCompletionService;
import com.hrishabh.algocracksubmissionservice.progress.service.ProgressOutboxService;
import com.hrishabh.algocracksubmissionservice.service.OracleExecutionService;
import com.hrishabh.algocracksubmissionservice.service.SubmissionFinalizer;
import com.hrishabh.algocracksubmissionservice.service.SubmissionProcessingService;
import com.hrishabh.algocracksubmissionservice.service.WebSocketService;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsApplier;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsOutboxService;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsReconciliationService;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsUpdateProcessor;
import com.hrishabh.algocracksubmissionservice.statistics.StatisticsReconcileGap;
import com.hrishabh.algocracksubmissionservice.statistics.StatisticsVersionConflictProbe;
import com.hrishabh.algocracksubmissionservice.statistics.model.QuestionStatisticsOutbox;
import com.hrishabh.algocracksubmissionservice.statistics.model.StatisticsOutboxStatus;
import com.hrishabh.algocracksubmissionservice.statistics.repository.QuestionStatisticsOutboxRepository;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan({
        "com.hrishabh.algocracksubmissionservice.models",
        "com.hrishabh.algocracksubmissionservice.progress.model",
        "com.hrishabh.algocracksubmissionservice.statistics.model"
})
@EnableJpaRepositories({
        "com.hrishabh.algocracksubmissionservice.repository",
        "com.hrishabh.algocracksubmissionservice.progress.repository",
        "com.hrishabh.algocracksubmissionservice.statistics.repository"
})
@EnableJpaAuditing
@Import({
        SubmissionProcessingService.class,
        SubmissionFinalizer.class,
        WebSocketService.class,
        PotdCompletionService.class,
        ProgressOutboxService.class,
        QuestionStatisticsOutboxService.class,
        QuestionStatisticsApplier.class,
        QuestionStatisticsUpdateProcessor.class,
        QuestionStatisticsReconciliationService.class,
        QuestionStatisticsReconciliationService.QuestionStatisticsReconcileTransaction.class
})
public class SubmissionPersistenceTestApplication {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SimpMessagingTemplate simpMessagingTemplate() {
        return Mockito.mock(SimpMessagingTemplate.class);
    }

    @Bean
    ExecutionAdapter executionAdapter() {
        return Mockito.mock(ExecutionAdapter.class);
    }

    @Bean
    OracleExecutionService oracleExecutionService() {
        return Mockito.mock(OracleExecutionService.class);
    }

    @Bean
    PipelineAssembler pipelineAssembler() {
        return Mockito.mock(PipelineAssembler.class);
    }

    @Bean
    ProblemServiceClient problemServiceClient() {
        return Mockito.mock(ProblemServiceClient.class);
    }

    @Bean
    AtomicBoolean statisticsConflictEnabled() {
        return new AtomicBoolean(false);
    }

    @Bean
    AtomicBoolean reconcileGapEnabled() {
        return new AtomicBoolean(false);
    }

    @Bean
    StatisticsReconcileGap statisticsReconcileGap(
            AtomicBoolean reconcileGapEnabled,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            SubmissionRepository submissionRepository,
            QuestionStatisticsOutboxRepository outboxRepository,
            QuestionStatisticsApplier applier) {
        org.springframework.transaction.support.TransactionTemplate requiresNew =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return questionId -> {
            if (!reconcileGapEnabled.compareAndSet(true, false)) {
                return;
            }
            Long outboxId = requiresNew.execute(status -> {
                Submission submission = submissionRepository.saveAndFlush(Submission.builder()
                        .submissionId(UUID.randomUUID().toString())
                        .userId("reconcile-gap")
                        .questionId(questionId)
                        .language("java")
                        .code("class Solution {}")
                        .status(SubmissionStatus.COMPLETED)
                        .verdict(SubmissionVerdict.ACCEPTED)
                        .runtimeMs(9)
                        .memoryKb(9)
                        .queuedAt(LocalDateTime.now().minusMinutes(1))
                        .completedAt(LocalDateTime.now())
                        .build());
                return outboxRepository.saveAndFlush(QuestionStatisticsOutbox.builder()
                        .submissionId(submission.getSubmissionId())
                        .questionId(questionId)
                        .status(StatisticsOutboxStatus.PENDING)
                        .attempts(0)
                        .nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                        .build()).getId();
            });
            applier.apply(outboxId);
        };
    }

    @Bean
    StatisticsVersionConflictProbe statisticsVersionConflictProbe(
            AtomicBoolean statisticsConflictEnabled,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            com.hrishabh.algocracksubmissionservice.repository.QuestionStatisticsRepository statisticsRepository) {
        org.springframework.transaction.support.TransactionTemplate requiresNew =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return questionId -> {
            if (!statisticsConflictEnabled.get()) {
                return;
            }
            requiresNew.executeWithoutResult(status -> {
                var stats = statisticsRepository.findByQuestionId(questionId).orElseThrow();
                int current = stats.getUniqueAttempts() == null ? 0 : stats.getUniqueAttempts();
                stats.setUniqueAttempts(current + 1);
                statisticsRepository.saveAndFlush(stats);
            });
        };
    }
}
