package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.adapter.ExecutionAdapter;
import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.dto.TestCaseDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.BatchExecutionResult;
import com.hrishabh.algocracksubmissionservice.dto.internal.TestCaseOutput;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.PotdResolveResponse;
import com.hrishabh.algocracksubmissionservice.judging.JudgingPipeline;
import com.hrishabh.algocracksubmissionservice.judging.JudgingResult;
import com.hrishabh.algocracksubmissionservice.judging.PipelineAssembler;
import com.hrishabh.algocracksubmissionservice.models.QuestionStatistics;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.progress.model.ProgressEventType;
import com.hrishabh.algocracksubmissionservice.progress.model.ProgressOutboxStatus;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankConstants;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserProgressRepository;
import com.hrishabh.algocracksubmissionservice.progress.service.PotdCompletionService;
import com.hrishabh.algocracksubmissionservice.progress.repository.DailyChallengeCompletionRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.ProgressUpdateOutboxRepository;
import com.hrishabh.algocracksubmissionservice.repository.QuestionStatisticsRepository;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsReconciliationService;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsUpdateProcessor;
import com.hrishabh.algocracksubmissionservice.statistics.model.QuestionStatisticsOutbox;
import com.hrishabh.algocracksubmissionservice.statistics.model.StatisticsOutboxStatus;
import com.hrishabh.algocracksubmissionservice.statistics.repository.QuestionStatisticsOutboxRepository;
import algocrack.submissiontest.SubmissionPersistenceTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = SubmissionPersistenceTestApplication.class, properties = {
        "spring.main.web-application-type=none",
        "spring.task.scheduling.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "statistics.dispatch-on-commit=true",
        "statistics.retry-backoff-ms=5",
        "spring.datasource.hikari.maximum-pool-size=32"
})
class SubmissionStatisticsConcurrencyTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("algocrack_submission_test")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("SPRING_DATASOURCE_URL", MYSQL::getJdbcUrl);
        registry.add("SPRING_DATASOURCE_USERNAME", MYSQL::getUsername);
        registry.add("SPRING_DATASOURCE_PASSWORD", MYSQL::getPassword);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    SubmissionProcessingService processingService;
    @Autowired
    SubmissionFinalizer submissionFinalizer;
    @Autowired
    SubmissionRepository submissionRepository;
    @Autowired
    QuestionStatisticsRepository statisticsRepository;
    @Autowired
    QuestionStatisticsOutboxRepository statisticsOutboxRepository;
    @Autowired
    ProgressUpdateOutboxRepository progressOutboxRepository;
    @Autowired
    DailyChallengeCompletionRepository dailyChallengeCompletionRepository;
    @Autowired
    QuestionStatisticsUpdateProcessor statisticsUpdateProcessor;
    @Autowired
    QuestionStatisticsReconciliationService reconciliationService;
    @Autowired
    ExecutionAdapter executionAdapter;
    @Autowired
    OracleExecutionService oracleExecutionService;
    @Autowired
    PipelineAssembler pipelineAssembler;
    @Autowired
    ProblemServiceClient problemServiceClient;
    @Autowired
    SimpMessagingTemplate messagingTemplate;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    AtomicBoolean statisticsConflictEnabled;
    @Autowired
    AtomicBoolean reconcileGapEnabled;
    @Autowired
    PotdCompletionService potdCompletionService;
    @Autowired
    UserProgressRepository userProgressRepository;

    private final JudgingPipeline judgingPipeline = mock(JudgingPipeline.class);

    @BeforeEach
    void clean() {
        statisticsConflictEnabled.set(false);
        reconcileGapEnabled.set(false);
        reset(executionAdapter, oracleExecutionService, pipelineAssembler, problemServiceClient, messagingTemplate);
        userProgressRepository.deleteAll();
        statisticsOutboxRepository.deleteAll();
        progressOutboxRepository.deleteAll();
        dailyChallengeCompletionRepository.deleteAll();
        statisticsRepository.deleteAll();
        submissionRepository.deleteAll();
        when(pipelineAssembler.assemble(any())).thenReturn(judgingPipeline);
        when(judgingPipeline.judge(any(), any(), any())).thenReturn(JudgingResult.passed("[0,1]", "[0,1]"));
    }

    @Test
    void concurrentSubmissionsUpdateTheSameQuestionExactlyOnceEach() throws Exception {
        long questionId = 1L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        stubSuccessfulExecution();

        int workers = 8;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            ids.add(queued(questionId, "user-" + i).getSubmissionId());
        }
        runTogether(ids.stream().map(id -> (Runnable) () -> processingService.processSubmission(id)).toList());

        for (String id : ids) {
            Submission saved = reload(id);
            assertEquals(SubmissionStatus.COMPLETED, saved.getStatus());
            assertEquals(SubmissionVerdict.ACCEPTED, saved.getVerdict());
        }
        statisticsOutboxRepository.findAll().stream()
                .filter(row -> row.getStatus() == StatisticsOutboxStatus.PENDING)
                .forEach(row -> statisticsUpdateProcessor.processOne(row.getId()));
        QuestionStatistics stats = statistics(questionId);
        assertEquals(workers, stats.getTotalSubmissions());
        assertEquals(workers, stats.getAcceptedSubmissions());
        assertEquals(workers, statisticsOutboxRepository.findAll().stream()
                .filter(row -> row.getStatus() == StatisticsOutboxStatus.PROCESSED)
                .count());
        assertEquals(workers, progressOutboxRepository.count());
    }

    @Test
    void optimisticLockConflictDoesNotRollBackAnAcceptedVerdict() {
        long questionId = 11L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        stubSuccessfulExecution();
        statisticsConflictEnabled.set(true);

        String id = queued(questionId, "user-lock").getSubmissionId();
        processingService.processSubmission(id);

        Submission saved = reload(id);
        assertEquals(SubmissionStatus.COMPLETED, saved.getStatus());
        assertEquals(SubmissionVerdict.ACCEPTED, saved.getVerdict());
        assertNotNull(saved.getCompletedAt());

        QuestionStatisticsOutbox outbox = statisticsOutboxRepository.findBySubmissionId(id).orElseThrow();
        assertEquals(StatisticsOutboxStatus.PENDING, outbox.getStatus());
        assertTrue(outbox.getAttempts() >= 1);
        assertNotNull(outbox.getLastError());
        assertTrue(outbox.getLastError().toLowerCase().contains("optimistic")
                || outbox.getLastError().toLowerCase().contains("stale")
                || outbox.getLastError().toLowerCase().contains("lock"));

        QuestionStatistics duringConflict = statistics(questionId);
        assertEquals(0, duringConflict.getTotalSubmissions());
        assertEquals(0, duringConflict.getAcceptedSubmissions());

        statisticsConflictEnabled.set(false);
        statisticsUpdateProcessor.processOne(outbox.getId());

        QuestionStatistics recovered = statistics(questionId);
        assertEquals(1, recovered.getTotalSubmissions());
        assertEquals(1, recovered.getAcceptedSubmissions());
        assertEquals(12, recovered.getBestRuntimeMs());
        assertEquals(StatisticsOutboxStatus.PROCESSED,
                statisticsOutboxRepository.findBySubmissionId(id).orElseThrow().getStatus());

        statisticsUpdateProcessor.processOne(outbox.getId());
        assertEquals(1, statistics(questionId).getTotalSubmissions());
        assertEquals(1, progressOutboxRepository.count());
    }

    @Test
    void duplicateResultDeliveryDoesNotDoubleCount() {
        long questionId = 12L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        stubSuccessfulExecution();
        String id = queued(questionId, "user-dup").getSubmissionId();

        processingService.processSubmission(id);
        processingService.processSubmission(id);
        statisticsUpdateProcessor.processOne(statisticsOutboxRepository.findBySubmissionId(id).orElseThrow().getId());

        assertEquals(SubmissionVerdict.ACCEPTED, reload(id).getVerdict());
        assertEquals(1, statistics(questionId).getTotalSubmissions());
        assertEquals(1, statistics(questionId).getAcceptedSubmissions());
        assertEquals(1, statisticsOutboxRepository.count());
        assertEquals(1, progressOutboxRepository.count());
        assertEquals(1, messagingResultCount(id));
    }

    @Test
    void repeatedAcceptedSubmissionsByTheSameUserCountOnceEach() {
        long questionId = 13L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        stubSuccessfulExecution();
        when(executionAdapter.execute(any())).thenReturn(successResult(100L), successResult(40L));
        when(problemServiceClient.resolvePotdCompletion(anyLong(), any(Instant.class))).thenReturn(
                PotdResolveResponse.builder()
                        .matched(true)
                        .qualifies(true)
                        .challengeId(77L)
                        .challengeDate("2026-10-19")
                        .questionId(questionId)
                        .normalizedDifficulty("MEDIUM")
                        .build());

        String first = queued(questionId, "same-user").getSubmissionId();
        String second = queued(questionId, "same-user").getSubmissionId();
        processingService.processSubmission(first);
        processingService.processSubmission(second);
        processingService.processSubmission(first);

        assertEquals(2, statistics(questionId).getTotalSubmissions());
        assertEquals(2, statistics(questionId).getAcceptedSubmissions());
        assertEquals(40, statistics(questionId).getBestRuntimeMs());
        assertEquals(2, progressOutboxRepository.count());
        assertEquals(1, dailyChallengeCompletionRepository.count());
        assertTrue(progressOutboxRepository.findAll().stream()
                .allMatch(row -> row.getStatus() == ProgressOutboxStatus.PENDING
                        && row.getEventType() == ProgressEventType.ACCEPTED_SUBMISSION));
    }

    @Test
    void retryExhaustionStaysPendingUntilReconciliation() {
        long questionId = 14L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        stubSuccessfulExecution();
        statisticsConflictEnabled.set(true);
        String id = queued(questionId, "user-exhaust").getSubmissionId();

        processingService.processSubmission(id);

        assertEquals(SubmissionStatus.COMPLETED, reload(id).getStatus());
        QuestionStatisticsOutbox pending = statisticsOutboxRepository.findBySubmissionId(id).orElseThrow();
        assertEquals(StatisticsOutboxStatus.PENDING, pending.getStatus());
        assertTrue(pending.getAttempts() >= 1);
        assertNotNull(pending.getLastError());
        assertEquals(0, statistics(questionId).getTotalSubmissions());

        statisticsConflictEnabled.set(false);
        QuestionStatistics repaired = reconciliationService.reconcile(questionId);
        assertEquals(1, repaired.getTotalSubmissions());
        assertEquals(1, repaired.getAcceptedSubmissions());
        assertEquals(StatisticsOutboxStatus.PROCESSED,
                statisticsOutboxRepository.findBySubmissionId(id).orElseThrow().getStatus());

        statisticsUpdateProcessor.processOne(pending.getId());
        assertEquals(1, statistics(questionId).getTotalSubmissions());
        assertEquals(1, statistics(questionId).getAcceptedSubmissions());
    }

    @Test
    void concurrentAcceptedAndRejectedSubmissionsDoNotLoseUpdates() throws Exception {
        long questionId = 15L;
        seedStatistics(questionId);
        Submission accepted = queued(questionId, "user-a");
        Submission rejected = queued(questionId, "user-b");

        runTogether(List.of(
                () -> submissionFinalizer.finalizeSubmission(accepted.getSubmissionId(), acceptedCompletion(30, 100)),
                () -> submissionFinalizer.finalizeSubmission(rejected.getSubmissionId(),
                        new SubmissionCompletion(
                                SubmissionVerdict.WRONG_ANSWER, 80, 200, "[]", null, null, "worker", true))));

        assertEquals(SubmissionVerdict.ACCEPTED, reload(accepted.getSubmissionId()).getVerdict());
        assertEquals(SubmissionVerdict.WRONG_ANSWER, reload(rejected.getSubmissionId()).getVerdict());
        QuestionStatistics stats = statistics(questionId);
        assertEquals(2, stats.getTotalSubmissions());
        assertEquals(1, stats.getAcceptedSubmissions());
        assertEquals(30, stats.getBestRuntimeMs());
        assertEquals(1, progressOutboxRepository.count());
    }

    @Test
    void concurrentAppliesOfOneOutboxIncrementOnce() throws Exception {
        long questionId = 16L;
        seedStatistics(questionId);
        Submission submission = queued(questionId, "user-once");
        submissionFinalizer.finalizeSubmission(submission.getSubmissionId(), acceptedCompletion(10, 10));
        long outboxId = statisticsOutboxRepository.findBySubmissionId(submission.getSubmissionId()).orElseThrow().getId();
        assertEquals(1, statistics(questionId).getTotalSubmissions());

        runTogether(List.of(
                () -> statisticsUpdateProcessor.processOne(outboxId),
                () -> statisticsUpdateProcessor.processOne(outboxId)));

        assertEquals(1, statistics(questionId).getTotalSubmissions());
        assertEquals(1, statistics(questionId).getAcceptedSubmissions());
    }

    @Test
    void completionEventIsVisibleOnlyAfterCommit() {
        long questionId = 17L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        stubSuccessfulExecution();
        String id = queued(questionId, "user-event").getSubmissionId();
        AtomicBoolean visible = new AtomicBoolean(false);
        TransactionTemplate requiresNew = requiresNew();
        doAnswer(invocation -> {
            Object payload = invocation.getArgument(1);
            if (payload instanceof Map<?, ?> map && "COMPLETED".equals(String.valueOf(map.get("status")))) {
                Boolean seen = requiresNew.execute(status -> submissionRepository.findBySubmissionId(id)
                        .map(row -> row.getStatus() == SubmissionStatus.COMPLETED
                                && row.getVerdict() == SubmissionVerdict.ACCEPTED)
                        .orElse(false));
                visible.set(Boolean.TRUE.equals(seen));
            }
            return null;
        }).when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        processingService.processSubmission(id);

        assertTrue(visible.get(), "completion event ran before the verdict commit");
        assertEquals(SubmissionStatus.COMPLETED, reload(id).getStatus());
    }

    @Test
    void rolledBackFinalizationDoesNotPublishOrPersist() {
        long questionId = 18L;
        String id = queued(questionId, "user-rollback").getSubmissionId();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.executeWithoutResult(status -> {
            submissionFinalizer.finalizeSubmission(id, acceptedCompletion(5, 5));
            status.setRollbackOnly();
        });

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        assertEquals(SubmissionStatus.QUEUED, reload(id).getStatus());
        assertNull(reload(id).getVerdict());
        assertEquals(0, statisticsOutboxRepository.count());
        assertEquals(0, progressOutboxRepository.count());
        assertEquals(0, statisticsRepository.count());
    }

    @Test
    void compilationErrorPersistsAndDoesNotCountAsAccepted() {
        long questionId = 19L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        when(executionAdapter.execute(any())).thenReturn(BatchExecutionResult.builder()
                .status(BatchExecutionResult.ExecutionStatus.COMPILATION_ERROR)
                .compilationOutput("cannot find symbol")
                .errorMessage("cannot find symbol")
                .outputs(List.of())
                .build());

        String id = queued(questionId, "user-compile").getSubmissionId();
        processingService.processSubmission(id);

        Submission saved = reload(id);
        assertEquals(SubmissionStatus.COMPLETED, saved.getStatus());
        assertEquals(SubmissionVerdict.COMPILATION_ERROR, saved.getVerdict());
        assertEquals("cannot find symbol", saved.getCompilationOutput());
        assertEquals(1, statistics(questionId).getTotalSubmissions());
        assertEquals(0, statistics(questionId).getAcceptedSubmissions());
        assertEquals(0, progressOutboxRepository.count());
        verify(oracleExecutionService, never()).executeOracle(anyLong(), any());
    }

    @Test
    void infrastructureFailureIsNotMarkedAccepted() {
        long questionId = 20L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        when(executionAdapter.execute(any())).thenThrow(new IllegalStateException("cxe unavailable"));

        String id = queued(questionId, "user-down").getSubmissionId();
        processingService.processSubmission(id);

        Submission saved = reload(id);
        assertEquals(SubmissionStatus.FAILED, saved.getStatus());
        assertNull(saved.getVerdict());
        assertEquals(0, statistics(questionId).getTotalSubmissions());
        assertEquals(0, statisticsOutboxRepository.count());
    }

    @Test
    void unsuccessfulExecutionCannotBeStoredAsAccepted() {
        long questionId = 21L;
        seedStatistics(questionId);
        String id = queued(questionId, "user-guard").getSubmissionId();

        submissionFinalizer.finalizeSubmission(id, new SubmissionCompletion(
                SubmissionVerdict.ACCEPTED, null, null, "[]", null, "pod failed", "worker", false));

        Submission saved = reload(id);
        assertEquals(SubmissionStatus.COMPLETED, saved.getStatus());
        assertEquals(SubmissionVerdict.INTERNAL_ERROR, saved.getVerdict());
        assertEquals(1, statistics(questionId).getTotalSubmissions());
        assertEquals(0, statistics(questionId).getAcceptedSubmissions());
        assertEquals(0, progressOutboxRepository.count());
    }

    @Test
    void missingStatisticsRowIsCreatedWithoutDoubleCounting() throws Exception {
        long questionId = 22L;
        stubMetadata(questionId);
        stubSuccessfulExecution();
        List<String> ids = List.of(
                queued(questionId, "user-new-a").getSubmissionId(),
                queued(questionId, "user-new-b").getSubmissionId());
        runTogether(ids.stream().map(id -> (Runnable) () -> processingService.processSubmission(id)).toList());

        assertEquals(2, statistics(questionId).getTotalSubmissions());
        assertEquals(2, statistics(questionId).getAcceptedSubmissions());
        assertEquals(2, statisticsOutboxRepository.count());
    }

    @Test
    void reconciliationRebuildsDriftedCountersFromCompletedSubmissions() {
        long questionId = 23L;
        QuestionStatistics drifted = seedStatistics(questionId);
        drifted.setTotalSubmissions(9);
        drifted.setAcceptedSubmissions(9);
        statisticsRepository.saveAndFlush(drifted);

        Submission accepted = completed(questionId, "user-r1", SubmissionVerdict.ACCEPTED, 15, 40);
        Submission rejected = completed(questionId, "user-r2", SubmissionVerdict.WRONG_ANSWER, 90, 80);
        statisticsOutboxRepository.saveAndFlush(pendingOutbox(accepted));
        statisticsOutboxRepository.saveAndFlush(pendingOutbox(rejected));

        QuestionStatistics repaired = reconciliationService.reconcile(questionId);
        assertEquals(2, repaired.getTotalSubmissions());
        assertEquals(1, repaired.getAcceptedSubmissions());
        assertEquals(15, repaired.getBestRuntimeMs());
        assertTrue(statisticsOutboxRepository.findAll().stream()
                .allMatch(row -> row.getStatus() == StatisticsOutboxStatus.PROCESSED));

        statisticsOutboxRepository.findAll()
                .forEach(row -> statisticsUpdateProcessor.processOne(row.getId()));
        assertEquals(2, statistics(questionId).getTotalSubmissions());
        assertEquals(1, statistics(questionId).getAcceptedSubmissions());
    }

    @Test
    void reconciliationKeepsASubmissionAppliedInTheReadGap() {
        long questionId = 31L;
        seedStatistics(questionId);
        Submission first = completed(questionId, "user-recon", SubmissionVerdict.ACCEPTED, 20, 20);
        statisticsOutboxRepository.saveAndFlush(QuestionStatisticsOutbox.builder()
                .submissionId(first.getSubmissionId())
                .questionId(questionId)
                .status(StatisticsOutboxStatus.PROCESSED)
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .build());
        statisticsRepository.findByQuestionId(questionId).ifPresent(stats -> {
            stats.setTotalSubmissions(1);
            stats.setAcceptedSubmissions(1);
            statisticsRepository.saveAndFlush(stats);
        });

        reconcileGapEnabled.set(true);
        QuestionStatistics reconciled = reconciliationService.reconcile(questionId);

        assertEquals(2, reconciled.getTotalSubmissions());
        assertEquals(2, reconciled.getAcceptedSubmissions());
        assertEquals(2, statistics(questionId).getTotalSubmissions());
        assertTrue(statisticsOutboxRepository.findAll().stream()
                .allMatch(row -> row.getStatus() == StatisticsOutboxStatus.PROCESSED));

        reconciliationService.reconcile(questionId);
        assertEquals(2, statistics(questionId).getTotalSubmissions());
        assertEquals(2, statistics(questionId).getAcceptedSubmissions());
    }

    @Test
    void concurrentReconcileAndFinalizersMatchCompletedSubmissions() throws Exception {
        long questionId = 32L;
        seedStatistics(questionId);
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(() -> reconciliationService.reconcile(questionId));
        for (int i = 0; i < 6; i++) {
            int worker = i;
            tasks.add(() -> {
                String id = queued(questionId, "user-recon-" + worker).getSubmissionId();
                submissionFinalizer.finalizeSubmission(id, acceptedCompletion(11, 11));
            });
        }
        runTogether(tasks);
        statisticsOutboxRepository.findAll().stream()
                .filter(row -> row.getStatus() == StatisticsOutboxStatus.PENDING)
                .forEach(row -> statisticsUpdateProcessor.processOne(row.getId()));

        int completedCount = requiresNew().execute(status -> (int) submissionRepository.findAll().stream()
                .filter(row -> row.getQuestionId().equals(questionId)
                        && row.getStatus() == SubmissionStatus.COMPLETED)
                .count());
        assertEquals(completedCount, statistics(questionId).getTotalSubmissions());
        assertEquals(completedCount, statistics(questionId).getAcceptedSubmissions());
        assertEquals(0, statisticsOutboxRepository.findAll().stream()
                .filter(row -> row.getStatus() == StatisticsOutboxStatus.PENDING)
                .count());
    }

    @Test
    void potdFailureStaysRecoverableWithoutDoubleCounting() {
        long questionId = 33L;
        seedStatistics(questionId);
        stubMetadata(questionId);
        stubSuccessfulExecution();
        when(problemServiceClient.resolvePotdCompletion(anyLong(), any(Instant.class)))
                .thenThrow(new RuntimeException("problem service down"));
        String id = queued(questionId, "potd-user").getSubmissionId();
        processingService.processSubmission(id);

        assertEquals(SubmissionStatus.COMPLETED, reload(id).getStatus());
        assertEquals(SubmissionVerdict.ACCEPTED, reload(id).getVerdict());
        assertEquals(0, dailyChallengeCompletionRepository.count());
        assertEquals(1, progressOutboxRepository.count());

        userProgressRepository.saveAndFlush(UserProgress.builder()
                .userId("potd-user")
                .rankAlgorithmVersion(RankConstants.ALGORITHM_VERSION)
                .rankTierCode("UNRANKED")
                .calculatedAt(LocalDateTime.now())
                .build());
        when(problemServiceClient.resolvePotdCompletion(anyLong(), any(Instant.class)))
                .thenReturn(PotdResolveResponse.builder()
                        .matched(true)
                        .qualifies(true)
                        .challengeId(77L)
                        .challengeDate("2026-10-19")
                        .questionId(questionId)
                        .normalizedDifficulty("MEDIUM")
                        .build());

        potdCompletionService.recordAcceptedSubmission(reload(id));
        potdCompletionService.recordAcceptedSubmission(reload(id));

        assertEquals(1, dailyChallengeCompletionRepository.count());
        UserProgress progress = userProgressRepository
                .findByUserIdAndRankAlgorithmVersion("potd-user", RankConstants.ALGORITHM_VERSION)
                .orElseThrow();
        assertEquals(1, progress.getCurrentPotdStreak());
        assertEquals(1, progress.getLongestPotdStreak());
    }

    @Test
    void failedStatusDoesNotOverwriteACommittedVerdict() {
        long questionId = 24L;
        seedStatistics(questionId);
        String id = queued(questionId, "user-terminal").getSubmissionId();
        assertTrue(submissionFinalizer.finalizeSubmission(id, acceptedCompletion(7, 8)));
        submissionFinalizer.markFailed(id, "late failure");
        assertFalse(submissionFinalizer.finalizeSubmission(id, new SubmissionCompletion(
                SubmissionVerdict.WRONG_ANSWER, 1, 1, "[]", null, null, "other", true)));

        Submission saved = reload(id);
        assertEquals(SubmissionStatus.COMPLETED, saved.getStatus());
        assertEquals(SubmissionVerdict.ACCEPTED, saved.getVerdict());
        assertEquals(1, statistics(questionId).getAcceptedSubmissions());
    }

    private void stubMetadata(long questionId) {
        when(problemServiceClient.getMetadata(anyLong(), anyString())).thenReturn(QuestionMetadataApiDto.builder()
                .questionId(questionId)
                .functionName("twoSum")
                .returnType("int[]")
                .paramNames(List.of("nums", "target"))
                .paramTypes(List.of("int[]", "int"))
                .build());
        when(problemServiceClient.getTestCases(anyLong(), anyString())).thenReturn(List.of(
                TestCaseDto.builder().questionId(questionId).input("[2,7]\n9").build()));
    }

    private void stubSuccessfulExecution() {
        when(executionAdapter.execute(any())).thenAnswer(invocation -> successResult(12L));
        when(oracleExecutionService.executeOracle(anyLong(), any())).thenReturn(successResult(4L));
        when(judgingPipeline.judge(any(), any(), any())).thenReturn(JudgingResult.passed("[0,1]", "[0,1]"));
    }

    private BatchExecutionResult successResult(long runtimeMs) {
        return BatchExecutionResult.builder()
                .status(BatchExecutionResult.ExecutionStatus.SUCCESS)
                .outputs(List.of(TestCaseOutput.builder().index(0).output("[0,1]").executionTimeMs(runtimeMs).build()))
                .totalRuntimeMs(runtimeMs)
                .peakMemoryKb(256L)
                .workerId("worker-test")
                .build();
    }

    private SubmissionCompletion acceptedCompletion(int runtimeMs, int memoryKb) {
        return new SubmissionCompletion(
                SubmissionVerdict.ACCEPTED, runtimeMs, memoryKb, "[]", null, null, "worker-test", true);
    }

    private QuestionStatistics seedStatistics(long questionId) {
        return statisticsRepository.saveAndFlush(QuestionStatistics.builder()
                .questionId(questionId)
                .totalSubmissions(0)
                .acceptedSubmissions(0)
                .build());
    }

    private Submission queued(long questionId, String userId) {
        return submissionRepository.saveAndFlush(Submission.builder()
                .submissionId(UUID.randomUUID().toString())
                .userId(userId)
                .questionId(questionId)
                .language("java")
                .code("class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0,1}; } }")
                .status(SubmissionStatus.QUEUED)
                .queuedAt(LocalDateTime.now())
                .build());
    }

    private Submission completed(long questionId, String userId, SubmissionVerdict verdict, int runtime, int memory) {
        return submissionRepository.saveAndFlush(Submission.builder()
                .submissionId(UUID.randomUUID().toString())
                .userId(userId)
                .questionId(questionId)
                .language("java")
                .code("class Solution {}")
                .status(SubmissionStatus.COMPLETED)
                .verdict(verdict)
                .runtimeMs(runtime)
                .memoryKb(memory)
                .queuedAt(LocalDateTime.now().minusMinutes(1))
                .completedAt(LocalDateTime.now())
                .build());
    }

    private QuestionStatisticsOutbox pendingOutbox(Submission submission) {
        return QuestionStatisticsOutbox.builder()
                .submissionId(submission.getSubmissionId())
                .questionId(submission.getQuestionId())
                .status(StatisticsOutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .build();
    }

    private Submission reload(String submissionId) {
        return requiresNew().execute(status -> submissionRepository.findBySubmissionId(submissionId).orElseThrow());
    }

    private QuestionStatistics statistics(long questionId) {
        return requiresNew().execute(status -> statisticsRepository.findByQuestionId(questionId).orElseThrow());
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private long messagingResultCount(String submissionId) {
        return org.mockito.Mockito.mockingDetails(messagingTemplate).getInvocations().stream()
                .filter(invocation -> "convertAndSend".equals(invocation.getMethod().getName()))
                .filter(invocation -> invocation.getArguments().length > 1)
                .filter(invocation -> {
                    Object payload = invocation.getArgument(1);
                    return payload instanceof Map<?, ?> map
                            && "COMPLETED".equals(String.valueOf(map.get("status")))
                            && submissionId.equals(String.valueOf(map.get("submissionId")));
                })
                .count();
    }

    private void runTogether(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    task.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
