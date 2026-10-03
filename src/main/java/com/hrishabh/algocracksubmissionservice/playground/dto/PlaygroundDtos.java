package com.hrishabh.algocracksubmissionservice.playground.dto;

import java.time.Instant;
import java.util.List;

public final class PlaygroundDtos {

    private PlaygroundDtos() {
    }

    public record PlaygroundCreateRequest(String title, String language, String sourceCode, String stdin) {
    }

    public record PlaygroundUpdateRequest(String title, String language, String sourceCode, String stdin) {
    }

    public record PlaygroundSummaryResponse(
            long id, String title, String language, Instant createdAt, Instant updatedAt) {
    }

    public record PlaygroundDetailResponse(
            long id,
            String title,
            String language,
            String sourceCode,
            String stdin,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PlaygroundPageResponse(
            List<PlaygroundSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record PlaygroundRunRequest(String language, String sourceCode, String stdin) {
    }

    public record PlaygroundRunResponse(
            String executionId,
            String status,
            String stdout,
            String stderr,
            String compilerOutput,
            Integer runtimeMs,
            Integer memoryKb,
            Integer exitCode,
            boolean outputTruncated) {
    }

    public record LanguageDescriptorResponse(
            String code, String displayName, String monacoLanguage, String starterSource) {
    }
}
