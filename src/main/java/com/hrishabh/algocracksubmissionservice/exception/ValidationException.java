package com.hrishabh.algocracksubmissionservice.exception;

/**
 * Exception thrown when request validation fails.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
