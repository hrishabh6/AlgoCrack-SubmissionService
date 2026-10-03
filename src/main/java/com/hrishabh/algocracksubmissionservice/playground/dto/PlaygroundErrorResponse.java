package com.hrishabh.algocracksubmissionservice.playground.dto;

import java.time.Instant;

public record PlaygroundErrorResponse(
        Instant timestamp,
        String requestId,
        int status,
        String code,
        String message) {
}
