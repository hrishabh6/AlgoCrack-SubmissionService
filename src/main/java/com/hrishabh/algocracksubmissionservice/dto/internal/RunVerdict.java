package com.hrishabh.algocracksubmissionservice.dto.internal;

/**
 * Verdict types for RUN mode execution.
 * Intentionally distinct from SubmissionVerdict to avoid semantic confusion.
 * 
 * PASSED_RUN ≠ ACCEPTED. Only SUBMIT produces authoritative verdicts.
 */
public enum RunVerdict {
    /**
     * User output matched oracle output for this testcase.
     * NOT equivalent to ACCEPTED - this is a soft verdict.
     */
    PASSED_RUN,

    /**
     * User output did not match oracle output.
     */
    FAILED_RUN,

    /**
     * Code failed to compile.
     */
    COMPILATION_ERROR_RUN,

    /**
     * Runtime error during execution.
     */
    RUNTIME_ERROR_RUN,

    /**
     * Execution exceeded time limit.
     */
    TIMEOUT_RUN,

    /**
     * Execution exceeded memory limit.
     */
    MEMORY_LIMIT_RUN,

    /**
     * Internal error during execution.
     */
    INTERNAL_ERROR_RUN
}
