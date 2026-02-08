package com.hrishabh.algocracksubmissionservice.dto.internal;

/**
 * Represents the intent of an execution request.
 * Used throughout the system for logging, metrics, and behavior branching.
 */
public enum ExecutionIntent {
    /**
     * RUN mode: Frontend-provided testcases, no persistence.
     * Uses soft verdicts (PASSED_RUN, FAILED_RUN).
     */
    RUN,

    /**
     * SUBMIT mode: HIDDEN testcases from DB, full persistence.
     * Uses authoritative verdicts (ACCEPTED, WRONG_ANSWER).
     */
    SUBMIT
}
