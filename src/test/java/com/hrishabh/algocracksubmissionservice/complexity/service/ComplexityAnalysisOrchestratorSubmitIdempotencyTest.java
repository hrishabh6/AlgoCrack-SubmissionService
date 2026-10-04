package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityBenchmarkOracleService;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityBenchmarkRunPersister;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityProfileCorrelationValidator;
import com.hrishabh.algocracksubmissionservice.complexity.client.CxeComplexityProfileClient;
import com.hrishabh.algocracksubmissionservice.complexity.client.CxeComplexityProfileTransportException;
import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.CasesRequest;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.CasesResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.GeneratedCaseDto;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.ProfileMetadataResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.SubmitRequest;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.SubmitResponse;
import com.hrishabh.algocracksubmissionservice.complexity.inference.DynamicGrowthInferenceEngine;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ComplexityHybridConfidenceModel;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ComplexityReconciliationEngine;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityBenchmarkRunRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityStaticFindingRepository;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.JavaStaticAnalyzer;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.support.ComplexityVersionConstants;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Crash/retry: execution identity is persisted (or deterministically re-derived) before CXE POST;
 * a lost submit response must not mint a second logical execution id.
 */
@ExtendWith(MockitoExtension.class)
class ComplexityAnalysisOrchestratorSubmitIdempotencyTest {

    private static final String ANALYSIS_ID = "analysis-retry-1";
    private static final String STABLE_EXECUTION_ID =
            ComplexityVersionConstants.PROFILE_EXECUTION_ID_PREFIX + ANALYSIS_ID;

    @Mock
    private ComplexityAnalysisRepository analysisRepository;
    @Mock
    private ComplexityStaticFindingRepository findingRepository;
    @Mock
    private ComplexityBenchmarkRunRepository benchmarkRunRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private ProblemServiceClient problemServiceClient;
    @Mock
    private ComplexityBenchmarkOracleService oracleService;
    @Mock
    private CxeComplexityProfileClient profileClient;
    @Mock
    private DynamicGrowthInferenceEngine inferenceEngine;
    @Mock
    private ComplexityReconciliationEngine reconciliationEngine;
    @Mock
    private ComplexityHybridConfidenceModel confidenceModel;
    @Mock
    private ComplexityAnalysisLeaseService leaseService;
    @Mock
    private ComplexityBenchmarkRunPersister benchmarkRunPersister;
    @Mock
    private com.hrishabh.algocracksubmissionservice.complexity.metrics.ComplexityMetrics complexityMetrics;

    private ComplexityAnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        ComplexityProperties properties = new ComplexityProperties();
        properties.setDynamicProfilingEnabled(true);
        orchestrator = new ComplexityAnalysisOrchestrator(
                analysisRepository,
                findingRepository,
                benchmarkRunRepository,
                submissionRepository,
                problemServiceClient,
                new JavaStaticAnalyzer(new JdkKnowledgeBase()),
                new JdkKnowledgeBase(),
                new ComplexityAnalysisJsonSupport(new ObjectMapper()),
                properties,
                new ComplexityProfileCorrelationValidator(),
                oracleService,
                profileClient,
                inferenceEngine,
                reconciliationEngine,
                confidenceModel,
                leaseService,
                benchmarkRunPersister,
                new ObjectMapper(),
                complexityMetrics);
    }

    @Test
    void persistsExecutionIdBeforeSubmitAndRetriesWithSameIdentity() {
        ComplexityAnalysis analysis = queuedAnalysis(null);
        stubLease(analysis);
        stubSubmitDependencies(analysis);
        when(analysisRepository.findByAnalysisId(ANALYSIS_ID)).thenReturn(Optional.of(analysis));
        when(leaseService.mutateIfLeaseHeld(eq(ANALYSIS_ID), any())).thenAnswer(inv -> {
            java.util.function.Consumer<ComplexityAnalysis> mutator = inv.getArgument(1);
            mutator.accept(analysis);
            return true;
        });
        when(profileClient.submit(any()))
                .thenThrow(new CxeComplexityProfileTransportException("response lost after CXE accept"))
                .thenReturn(new SubmitResponse(STABLE_EXECUTION_ID, "QUEUED"));
        when(profileClient.poll(STABLE_EXECUTION_ID)).thenReturn(Optional.empty());

        orchestrator.advance(ANALYSIS_ID);
        assertEquals(ComplexityProcessingStatus.BENCHMARKING, analysis.getStatus());
        orchestrator.advance(ANALYSIS_ID);

        ArgumentCaptor<SubmitRequest> captor = ArgumentCaptor.forClass(SubmitRequest.class);
        verify(profileClient, times(2)).submit(captor.capture());
        assertEquals(STABLE_EXECUTION_ID, captor.getAllValues().get(0).executionId());
        assertEquals(STABLE_EXECUTION_ID, captor.getAllValues().get(1).executionId());
        verify(leaseService, times(1)).mutateIfLeaseHeld(eq(ANALYSIS_ID), any());
        assertNotEquals(ComplexityProcessingStatus.COMPLETED, analysis.getStatus());
    }

    @Test
    void skipsRePersistWhenExecutionIdAlreadyStored() {
        ComplexityAnalysis analysis = queuedAnalysis(STABLE_EXECUTION_ID);
        stubLease(analysis);
        stubSubmitDependencies(analysis);
        when(analysisRepository.findByAnalysisId(ANALYSIS_ID)).thenReturn(Optional.of(analysis));
        when(profileClient.submit(any())).thenReturn(new SubmitResponse(STABLE_EXECUTION_ID, "QUEUED"));

        orchestrator.advance(ANALYSIS_ID);

        verify(leaseService, times(1)).mutateIfLeaseHeld(eq(ANALYSIS_ID), any());
        ArgumentCaptor<SubmitRequest> captor = ArgumentCaptor.forClass(SubmitRequest.class);
        verify(profileClient, times(1)).submit(captor.capture());
        assertEquals(STABLE_EXECUTION_ID, captor.getValue().executionId());
    }

    private ComplexityAnalysis queuedAnalysis(String profileExecutionId) {
        return ComplexityAnalysis.builder()
                .analysisId(ANALYSIS_ID)
                .submissionId("sub-1")
                .ownerUserId("user-1")
                .questionId(42L)
                .language("java")
                .status(ComplexityProcessingStatus.BENCHMARK_QUEUED)
                .activeSlot(ComplexityAnalysis.ACTIVE_SLOT_VALUE)
                .profileExecutionId(profileExecutionId)
                .profileVersion("v1")
                .profileHash("profile-hash")
                .generatorVersion("gv1")
                .harnessVersion("hv1")
                .build();
    }

    private void stubLease(ComplexityAnalysis analysis) {
        when(leaseService.tryClaim(ANALYSIS_ID)).thenReturn(true);
        when(leaseService.loadForLeaseWrite(ANALYSIS_ID)).thenReturn(Optional.of(analysis));
    }

    private void stubSubmitDependencies(ComplexityAnalysis analysis) {
        ProfileMetadataResponse profile = new ProfileMetadataResponse(
                42L,
                "java",
                "pid",
                "P",
                "v1",
                "profile-hash",
                "gk",
                "gv1",
                List.of(),
                Map.of("n", List.of(8)),
                Map.of("n", 1024),
                List.of("R"),
                1,
                3,
                1000,
                5000);
        GeneratedCaseDto generated = new GeneratedCaseDto(
                "P", "v1", "profile-hash", "gv1", "c1", "identity-1",
                Map.of("n", 8), "R", "seed", "{}", null, "input-hash-1");
        when(problemServiceClient.getActiveComplexityProfile(42L, "java")).thenReturn(Optional.of(profile));
        when(problemServiceClient.generateComplexityProfileCases(eq(42L), any(CasesRequest.class)))
                .thenReturn(new CasesResponse(
                        42L, "java", "pid", "P", "v1", "profile-hash", "gk", "gv1", List.of(generated)));
        when(problemServiceClient.getMetadata(42L, "java"))
                .thenReturn(QuestionMetadataApiDto.builder().questionId(42L).language("java").build());
        when(submissionRepository.findBySubmissionId("sub-1"))
                .thenReturn(Optional.of(Submission.builder().submissionId("sub-1").code("class S {}").build()));
        when(oracleService.expectedOutputsForCases(eq(42L), any()))
                .thenReturn(List.of(new ComplexityBenchmarkOracleService.OracleCaseOutput(
                        "c1", "input-hash-1", new ObjectMapper().createArrayNode())));
        lenient().when(analysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
}
