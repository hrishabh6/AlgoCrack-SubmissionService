package com.hrishabh.algocracksubmissionservice.exception;

/**
 * Exception thrown when rate limit is exceeded.
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
