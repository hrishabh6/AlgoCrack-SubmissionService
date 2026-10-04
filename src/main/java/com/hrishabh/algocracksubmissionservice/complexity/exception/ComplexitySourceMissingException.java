package com.hrishabh.algocracksubmissionservice.complexity.exception;

public class ComplexitySourceMissingException extends RuntimeException {

    public ComplexitySourceMissingException() {
        super("Submission source is missing");
    }
}
