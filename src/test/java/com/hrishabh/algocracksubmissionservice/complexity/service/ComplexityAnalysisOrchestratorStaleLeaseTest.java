package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityBenchmarkRunPersister;
import com.hrishabh.algocracksubmissionservice.complexity.client.CxeComplexityProfileClient;
import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.inference.DynamicGrowthInferenceEngine;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ComplexityHybridConfidenceModel;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ComplexityReconciliationEngine;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityBenchmarkRunRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityStaticFindingRepository;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.JavaStaticAnalyzer;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexityAnalysisOrchestratorStaleLeaseTest {

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

    private ComplexityAnalysisOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        ComplexityProperties properties = new ComplexityProperties();
        properties.setDynamicProfilingEnabled(false);
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
                null,
                null,
                profileClient,
                inferenceEngine,
                reconciliationEngine,
                confidenceModel,
                leaseService,
                benchmarkRunPersister,
                new ObjectMapper());
    }

    @Test
    void staleWorkerCannotFinalizeAfterLeaseLost() {
        ComplexityAnalysis analysis = ComplexityAnalysis.builder()
                .analysisId("a-stale")
                .submissionId("sub-1")
                .status(ComplexityProcessingStatus.STATIC_ANALYZING)
                .resultKind(ComplexityResultKind.STATIC_ONLY)
                .activeSlot(ComplexityAnalysis.ACTIVE_SLOT_VALUE)
                .language("java")
                .build();
        when(analysisRepository.findByAnalysisId("a-stale")).thenReturn(Optional.of(analysis));
        when(leaseService.tryClaim("a-stale")).thenReturn(true);
        when(leaseService.loadForLeaseWrite("a-stale")).thenReturn(Optional.empty());

        orchestrator.advance("a-stale");

        verify(analysisRepository, never()).save(any());
    }
}
