package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityForbiddenException;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexitySubmissionNotFoundException;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ComplexitySubmissionAccessService {

    private final SubmissionRepository submissionRepository;
    private final ComplexitySubmissionEligibilityService eligibilityService;

    public Submission requireOwnedSubmission(String submissionPublicId, String userId) {
        Submission submission = submissionRepository.findBySubmissionId(submissionPublicId)
                .orElseThrow(ComplexitySubmissionNotFoundException::new);
        if (!submission.getUserId().equals(userId)) {
            throw new ComplexityForbiddenException();
        }
        return submission;
    }

    public Submission requireEligibleOwnedSubmission(String submissionPublicId, String userId) {
        Submission submission = requireOwnedSubmission(submissionPublicId, userId);
        eligibilityService.validateEligible(submission);
        return submission;
    }
}
