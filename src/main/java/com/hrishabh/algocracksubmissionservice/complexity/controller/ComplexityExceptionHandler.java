package com.hrishabh.algocracksubmissionservice.complexity.controller;

import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityErrorResponse;
import com.hrishabh.algocracksubmissionservice.complexity.exception.*;
import com.hrishabh.algocracksubmissionservice.logging.RequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(assignableTypes = ComplexityAnalysisController.class)
public class ComplexityExceptionHandler {

    @ExceptionHandler(ComplexitySubmissionNotFoundException.class)
    public ResponseEntity<ComplexityErrorResponse> submissionNotFound() {
        return build(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "Submission not found");
    }

    @ExceptionHandler(ComplexityAnalysisNotFoundException.class)
    public ResponseEntity<ComplexityErrorResponse> analysisNotFound() {
        return build(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "Complexity analysis not found");
    }

    @ExceptionHandler(ComplexityForbiddenException.class)
    public ResponseEntity<ComplexityErrorResponse> forbidden() {
        return build(HttpStatus.FORBIDDEN, "ANALYSIS_FORBIDDEN", "Not allowed to access complexity analysis for this submission");
    }

    @ExceptionHandler(ComplexityNotAcceptedException.class)
    public ResponseEntity<ComplexityErrorResponse> notAccepted(ComplexityNotAcceptedException ex) {
        return build(HttpStatus.CONFLICT, "SUBMISSION_NOT_ACCEPTED", safeMessage(ex.getMessage()));
    }

    @ExceptionHandler(ComplexityLanguageUnsupportedException.class)
    public ResponseEntity<ComplexityErrorResponse> languageUnsupported(ComplexityLanguageUnsupportedException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "LANGUAGE_UNSUPPORTED", safeMessage(ex.getMessage()));
    }

    @ExceptionHandler(ComplexitySourceMissingException.class)
    public ResponseEntity<ComplexityErrorResponse> sourceMissing() {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_MISSING", "Submission source is missing");
    }

    private ResponseEntity<ComplexityErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ComplexityErrorResponse(
                Instant.now(),
                RequestContext.getRequestId(),
                status.value(),
                code,
                message));
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Request failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
