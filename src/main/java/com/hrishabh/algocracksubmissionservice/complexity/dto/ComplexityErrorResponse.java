package com.hrishabh.algocracksubmissionservice.complexity.dto;

import java.time.Instant;

public record ComplexityErrorResponse(
        Instant timestamp,
        String requestId,
        int status,
        String code,
        String message) {
}
