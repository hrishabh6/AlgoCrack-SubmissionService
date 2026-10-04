package com.hrishabh.algocracksubmissionservice.complexity;

import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityForbiddenException;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexitySubmissionNotFoundException;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexitySubmissionAccessService;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexitySubmissionEligibilityService;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexitySubmissionAccessServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    private ComplexitySubmissionAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new ComplexitySubmissionAccessService(
                submissionRepository, new ComplexitySubmissionEligibilityService());
    }

    @Test
    void rejectsForeignUser() {
        Submission submission = Submission.builder()
                .submissionId("sub-1")
                .userId("owner")
                .questionId(1L)
                .language("java")
                .code("class Main {}")
                .status(SubmissionStatus.COMPLETED)
                .verdict(SubmissionVerdict.ACCEPTED)
                .build();
        when(submissionRepository.findBySubmissionId("sub-1")).thenReturn(Optional.of(submission));

        assertThrows(ComplexityForbiddenException.class,
                () -> accessService.requireOwnedSubmission("sub-1", "other-user"));
    }

    @Test
    void rejectsMissingSubmission() {
        when(submissionRepository.findBySubmissionId("missing")).thenReturn(Optional.empty());
        assertThrows(ComplexitySubmissionNotFoundException.class,
                () -> accessService.requireOwnedSubmission("missing", "user-1"));
    }
}
