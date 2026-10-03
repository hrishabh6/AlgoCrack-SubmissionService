package com.hrishabh.algocracksubmissionservice.playground.controller;

import com.hrishabh.algocracksubmissionservice.exception.TooManyRequestsException;
import com.hrishabh.algocracksubmissionservice.exception.ValidationException;
import com.hrishabh.algocracksubmissionservice.logging.RequestContext;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundErrorResponse;
import com.hrishabh.algocracksubmissionservice.playground.exception.PlaygroundConflictException;
import com.hrishabh.algocracksubmissionservice.playground.exception.PlaygroundNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(assignableTypes = PlaygroundController.class)
public class PlaygroundExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<PlaygroundErrorResponse> validation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", safeMessage(ex.getMessage()));
    }

    @ExceptionHandler(PlaygroundNotFoundException.class)
    public ResponseEntity<PlaygroundErrorResponse> notFound(PlaygroundNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "PLAYGROUND_NOT_FOUND", safeMessage(ex.getMessage()));
    }

    @ExceptionHandler(PlaygroundConflictException.class)
    public ResponseEntity<PlaygroundErrorResponse> conflict(PlaygroundConflictException ex) {
        return build(HttpStatus.CONFLICT, "EXECUTION_ALREADY_RUNNING", safeMessage(ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<PlaygroundErrorResponse> rateLimited(TooManyRequestsException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", safeMessage(ex.getMessage()));
    }

    private ResponseEntity<PlaygroundErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new PlaygroundErrorResponse(
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
