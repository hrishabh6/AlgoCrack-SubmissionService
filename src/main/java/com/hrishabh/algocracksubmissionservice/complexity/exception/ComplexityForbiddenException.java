package com.hrishabh.algocracksubmissionservice.complexity.exception;

public class ComplexityForbiddenException extends RuntimeException {

    public ComplexityForbiddenException() {
        super("Not allowed to access complexity analysis for this submission");
    }
}
