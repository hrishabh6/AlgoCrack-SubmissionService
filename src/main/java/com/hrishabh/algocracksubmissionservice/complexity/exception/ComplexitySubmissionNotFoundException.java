package com.hrishabh.algocracksubmissionservice.complexity.exception;

public class ComplexitySubmissionNotFoundException extends RuntimeException {

    public ComplexitySubmissionNotFoundException() {
        super("Submission not found");
    }
}
