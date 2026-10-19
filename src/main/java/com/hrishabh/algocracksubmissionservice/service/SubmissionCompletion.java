package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;

public record SubmissionCompletion(
        SubmissionVerdict verdict,
        Integer runtimeMs,
        Integer memoryKb,
        String testResults,
        String compilationOutput,
        String errorMessage,
        String workerId,
        boolean executionSucceeded) {
}
