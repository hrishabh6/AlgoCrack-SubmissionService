package com.hrishabh.algocracksubmissionservice.complexity.client;

public class CxeComplexityProfileException extends RuntimeException {

    private final String errorCode;

    public CxeComplexityProfileException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
