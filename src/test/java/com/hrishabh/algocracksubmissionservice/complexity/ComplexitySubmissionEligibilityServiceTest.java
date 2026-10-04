package com.hrishabh.algocracksubmissionservice.complexity;

import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityLanguageUnsupportedException;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityNotAcceptedException;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexitySourceMissingException;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexitySubmissionEligibilityService;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComplexitySubmissionEligibilityServiceTest {

    private final ComplexitySubmissionEligibilityService service = new ComplexitySubmissionEligibilityService();

    @Test
    void acceptsCompletedAcceptedJavaSubmission() {
        Submission submission = eligibleJavaSubmission();
        assertDoesNotThrow(() -> service.validateEligible(submission));
    }

    @Test
    void rejectsNonAcceptedVerdict() {
        Submission submission = eligibleJavaSubmission();
        submission.setVerdict(SubmissionVerdict.WRONG_ANSWER);
        assertThrows(ComplexityNotAcceptedException.class, () -> service.validateEligible(submission));
    }

    @Test
    void rejectsIncompleteStatus() {
        Submission submission = eligibleJavaSubmission();
        submission.setStatus(SubmissionStatus.RUNNING);
        assertThrows(ComplexityNotAcceptedException.class, () -> service.validateEligible(submission));
    }

    @Test
    void rejectsNonJavaLanguage() {
        Submission submission = eligibleJavaSubmission();
        submission.setLanguage("python");
        assertThrows(ComplexityLanguageUnsupportedException.class, () -> service.validateEligible(submission));
    }

    @Test
    void rejectsBlankSource() {
        Submission submission = eligibleJavaSubmission();
        submission.setCode("   ");
        assertThrows(ComplexitySourceMissingException.class, () -> service.validateEligible(submission));
    }

    private static Submission eligibleJavaSubmission() {
        return Submission.builder()
                .submissionId("sub-1")
                .userId("user-1")
                .questionId(42L)
                .language("java")
                .code("class Main {}")
                .status(SubmissionStatus.COMPLETED)
                .verdict(SubmissionVerdict.ACCEPTED)
                .build();
    }
}
