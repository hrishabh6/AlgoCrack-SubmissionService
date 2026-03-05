package com.hrishabh.algocracksubmissionservice.models;

/**
 * Represents the final verdict/result of a code submission.
 */
public enum SubmissionVerdict {
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILATION_ERROR,
    INTERNAL_ERROR
}
