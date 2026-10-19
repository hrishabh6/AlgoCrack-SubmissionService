package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;

/**
 * Immutable copy of the submission fields needed to execute code.
 * Kept off the persistence context so CXE calls are not inside a transaction.
 */
public record SubmissionSnapshot(
        String submissionId,
        String userId,
        Long questionId,
        String language,
        String code,
        SubmissionStatus status) {
}
