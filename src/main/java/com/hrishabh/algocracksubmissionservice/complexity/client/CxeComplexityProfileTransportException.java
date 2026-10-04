package com.hrishabh.algocracksubmissionservice.complexity.client;

/**
 * Ambiguous CXE transport failure (timeout, reset, lost response). Does not prove rejection.
 */
public class CxeComplexityProfileTransportException extends RuntimeException {

    public CxeComplexityProfileTransportException(String message) {
        super(message);
    }

    public CxeComplexityProfileTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
