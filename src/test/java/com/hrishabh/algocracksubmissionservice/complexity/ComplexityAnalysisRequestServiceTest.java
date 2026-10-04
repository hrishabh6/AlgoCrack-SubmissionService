package com.hrishabh.algocracksubmissionservice.complexity;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import com.hrishabh.algocracksubmissionservice.complexity.service.*;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplexityAnalysisRequestServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ComplexityAnalysisRepository complexityAnalysisRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.hrishabh.algocracksubmissionservice.complexity.metrics.ComplexityMetrics complexityMetrics;

    private ComplexityAnalysisRequestService requestService;

    @BeforeEach
    void setUp() {
        ComplexityProperties properties = new ComplexityProperties();
        ComplexitySubmissionAccessService accessService = new ComplexitySubmissionAccessService(
                submissionRepository, new ComplexitySubmissionEligibilityService());
        requestService = new ComplexityAnalysisRequestService(
                accessService,
                complexityAnalysisRepository,
                new ComplexityAnalysisMapper(properties, new ComplexityAnalysisJsonSupport(new com.fasterxml.jackson.databind.ObjectMapper())),
                eventPublisher,
                complexityMetrics);
    }

    @Test
    void createsQueuedAnalysisWithoutMutatingSubmission() {
        Submission submission = eligibleSubmission();
        when(submissionRepository.findBySubmissionId("sub-1")).thenReturn(Optional.of(submission));
        when(complexityAnalysisRepository.findBySubmissionIdAndActiveSlot("sub-1", ComplexityAnalysis.ACTIVE_SLOT_VALUE))
                .thenReturn(Optional.empty());
        when(complexityAnalysisRepository.save(any())).thenAnswer(invocation -> {
            ComplexityAnalysis row = invocation.getArgument(0);
            row.setId(1L);
            return row;
        });

        var response = requestService.requestAnalysis("sub-1", "user-1");

        assertFalse(response.reused());
        assertEquals("QUEUED", response.status());
        verify(submissionRepository, never()).save(any());
        ArgumentCaptor<ComplexityAnalysis> captor = ArgumentCaptor.forClass(ComplexityAnalysis.class);
        verify(complexityAnalysisRepository).save(captor.capture());
        ComplexityAnalysis saved = captor.getValue();
        assertEquals(ComplexityProcessingStatus.QUEUED, saved.getStatus());
        assertEquals(ComplexityAnalysis.ACTIVE_SLOT_VALUE, saved.getActiveSlot());
        assertEquals("JAVA", saved.getLanguage());
        assertNotNull(saved.getSourceSha256());
        assertEquals(64, saved.getSourceSha256().length());
    }

    @Test
    void returnsExistingActiveAnalysisOnDuplicateRequest() {
        Submission submission = eligibleSubmission();
        ComplexityAnalysis active = ComplexityAnalysis.builder()
                .analysisId("analysis-active")
                .submissionId("sub-1")
                .status(ComplexityProcessingStatus.QUEUED)
                .build();
        when(submissionRepository.findBySubmissionId("sub-1")).thenReturn(Optional.of(submission));
        when(complexityAnalysisRepository.findBySubmissionIdAndActiveSlot("sub-1", ComplexityAnalysis.ACTIVE_SLOT_VALUE))
                .thenReturn(Optional.of(active));

        var response = requestService.requestAnalysis("sub-1", "user-1");

        assertTrue(response.reused());
        assertEquals("analysis-active", response.analysisId());
        verify(complexityAnalysisRepository, never()).save(any());
    }

    @Test
    void recoversActiveAnalysisWhenConcurrentInsertHitsUniqueConstraint() {
        Submission submission = eligibleSubmission();
        ComplexityAnalysis active = ComplexityAnalysis.builder()
                .analysisId("analysis-race")
                .submissionId("sub-1")
                .status(ComplexityProcessingStatus.QUEUED)
                .build();
        when(submissionRepository.findBySubmissionId("sub-1")).thenReturn(Optional.of(submission));
        when(complexityAnalysisRepository.findBySubmissionIdAndActiveSlot("sub-1", ComplexityAnalysis.ACTIVE_SLOT_VALUE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(active));
        when(complexityAnalysisRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate active_slot"));

        var response = requestService.requestAnalysis("sub-1", "user-1");

        assertTrue(response.reused());
        assertEquals("analysis-race", response.analysisId());
    }

    private static Submission eligibleSubmission() {
        return Submission.builder()
                .submissionId("sub-1")
                .userId("user-1")
                .questionId(99L)
                .language("java")
                .code("public class Main { }")
                .status(SubmissionStatus.COMPLETED)
                .verdict(SubmissionVerdict.ACCEPTED)
                .build();
    }
}
