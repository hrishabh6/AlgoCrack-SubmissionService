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
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplexityStaticAnalysisPipelineTest {

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
                analysisRepository,
                findingRepository,
                submissionRepository,
                problemServiceClient,
                new JavaStaticAnalyzer(new JdkKnowledgeBase()),
                new JdkKnowledgeBase(),
                new ComplexityAnalysisJsonSupport(new ObjectMapper()));
    }

    @Test
    void completesStaticAnalysisAndReleasesActiveSlotWithoutMutatingSubmission() {
        ComplexityAnalysis analysis = ComplexityAnalysis.builder()
                .analysisId("a-1")
                .submissionId("sub-1")
                .ownerUserId("u-1")
                .questionId(1L)
                .language("JAVA")
                .status(ComplexityProcessingStatus.QUEUED)
                .sourceSha256("abc")
                .activeSlot(ComplexityAnalysis.ACTIVE_SLOT_VALUE)
                .build();

        Submission submission = Submission.builder()
                .submissionId("sub-1")
                .userId("u-1")
                .questionId(1L)
                .language("java")
                .code("class Solution { public int solve() { return 1; } }")
                .status(SubmissionStatus.COMPLETED)
                .verdict(SubmissionVerdict.ACCEPTED)
                .build();

        when(analysisRepository.findByAnalysisId("a-1")).thenReturn(Optional.of(analysis));
        when(submissionRepository.findBySubmissionId("sub-1")).thenReturn(Optional.of(submission));
        when(analysisRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(pipeline.processQueuedAnalysis("a-1"));

        ArgumentCaptor<ComplexityAnalysis> captor = ArgumentCaptor.forClass(ComplexityAnalysis.class);
        verify(analysisRepository, atLeastOnce()).save(captor.capture());
        ComplexityAnalysis terminal = captor.getAllValues().getLast();
        assertEquals(ComplexityProcessingStatus.COMPLETED, terminal.getStatus());
        assertNull(terminal.getActiveSlot());
        assertEquals(ComplexityResultKind.STATIC_ONLY, terminal.getResultKind());
        assertNotNull(terminal.getTimeBigO());
        verify(submissionRepository, never()).save(any());
    }
}
