package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityStaticFindingRepository;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.JavaStaticAnalyzer;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplexityStaticAnalysisPipelineTest {

    @Mock
    private ComplexityAnalysisOrchestrator orchestrator;
    @Mock
    private ComplexityAnalysisRepository analysisRepository;
    @Mock
    private ComplexityStaticFindingRepository findingRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private ProblemServiceClient problemServiceClient;

    private ComplexityStaticAnalysisPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new ComplexityStaticAnalysisPipeline(
                orchestrator,
                analysisRepository,
                findingRepository,
                submissionRepository,
                problemServiceClient,
                new JavaStaticAnalyzer(new JdkKnowledgeBase()),
                new JdkKnowledgeBase(),
                new ComplexityAnalysisJsonSupport(new ObjectMapper()));
    }

    @Test
    void delegatesToOrchestratorWithoutMutatingSubmission() {
        ComplexityAnalysis completed = ComplexityAnalysis.builder()
                .analysisId("a-1")
                .status(ComplexityProcessingStatus.COMPLETED)
                .build();
        when(analysisRepository.findByAnalysisId("a-1")).thenReturn(Optional.of(completed));

        assertTrue(pipeline.processQueuedAnalysis("a-1"));
        verify(orchestrator).advance("a-1");
        verify(submissionRepository, never()).save(any());
    }
}
