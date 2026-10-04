package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityLanguageUnsupportedException;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityNotAcceptedException;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexitySourceMissingException;
import com.hrishabh.algocracksubmissionservice.complexity.support.ComplexityLanguageSupport;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import org.springframework.stereotype.Service;

@Service
public class ComplexitySubmissionEligibilityService {

    public void validateEligible(Submission submission) {
        if (submission.getStatus() != SubmissionStatus.COMPLETED) {
            throw new ComplexityNotAcceptedException("Submission is not completed");
        }
        if (submission.getVerdict() != SubmissionVerdict.ACCEPTED) {
            throw new ComplexityNotAcceptedException("Submission is not accepted");
        }
        if (submission.getCode() == null || submission.getCode().isBlank()) {
            throw new ComplexitySourceMissingException();
        }
        if (submission.getQuestionId() == null) {
            throw new ComplexityNotAcceptedException("Submission has no question");
        }
        if (!ComplexityLanguageSupport.isEligibleJavaLanguage(submission.getLanguage())) {
            throw new ComplexityLanguageUnsupportedException("Only Java is supported in V1");
        }
    }
}
