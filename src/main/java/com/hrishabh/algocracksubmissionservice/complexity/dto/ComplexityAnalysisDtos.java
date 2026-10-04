package com.hrishabh.algocracksubmissionservice.complexity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

public final class ComplexityAnalysisDtos {

    private ComplexityAnalysisDtos() {
    }

    public record ComplexityAnalysisRequestResponse(
            String analysisId,
            String submissionId,
            String status,
            boolean reused,
            long pollAfterMs) {
    }

    public record ComplexityAnalysisSummaryResponse(
            String analysisId,
            String submissionId,
            String status,
            String resultKind,
            String language,
            LocalDateTime requestedAt,
            LocalDateTime completedAt) {
    }

    public record ComplexityAnalysisHistoryResponse(
            List<ComplexityAnalysisSummaryResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ComplexityEstimateResponse(
            String expression,
            String bigO,
            String boundBasis,
            String confidence) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ComplexityAnalysisDetailResponse(
            String analysisId,
            String submissionId,
            String status,
            String resultKind,
            String language,
            LocalDateTime requestedAt,
            LocalDateTime completedAt,
            ComplexityEstimateResponse time,
            ComplexityEstimateResponse space,
            List<ComplexityVariableResponse> variables,
            ComplexityEvidenceResponse evidence,
            List<String> limitations,
            ComplexityVersionsResponse versions,
            Boolean reused) {
    }

    public record ComplexityVariableResponse(String name, String meaning) {
    }

    public record ComplexityEvidenceResponse(List<String> staticEvidence, List<String> dynamic) {
    }

    public record ComplexityVersionsResponse(
            String analyzer,
            String inference,
            String confidenceModel,
            String knowledgeBase,
            String profile,
            String generator,
            String harness,
            String measurementPolicy) {
    }
}
