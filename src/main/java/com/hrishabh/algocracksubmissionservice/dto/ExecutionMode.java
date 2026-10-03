package com.hrishabh.algocracksubmissionservice.dto;

/**
 * CXE execution mode mirror. Problem RUN/SUBMIT both map to {@link #SUBMISSION}.
 */
public enum ExecutionMode {
    SUBMISSION,
    PLAYGROUND,
    COMPLEXITY_PROFILE
}
