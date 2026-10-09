package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model;

/**
 * Structured reason codes for static analysis completeness and confidence (Batch 0).
 */
public enum StaticAnalysisReasonCode {
    UNSUPPORTED_STATEMENT,
    UNSUPPORTED_EXPRESSION,
    UNVISITED_CHILD,
    OPAQUE_CALL,
    UNKNOWN_LOOP_BOUND,
    LOOP_PROGRESS_NOT_PROVEN,
    UNKNOWN_RECURSIVE_CYCLE,
    INCOMPLETE_TIME_ANALYSIS,
    INCOMPLETE_SPACE_ANALYSIS
}
