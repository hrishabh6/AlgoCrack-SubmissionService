package com.hrishabh.algocracksubmissionservice.models;

/**
 * Represents the current processing status of a code submission.
 */
public enum SubmissionStatus {
    QUEUED,
    COMPILING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
