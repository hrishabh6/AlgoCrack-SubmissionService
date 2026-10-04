package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisDetailResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisRequestResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisSummaryResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityEstimateResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityEvidenceResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityVariableResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityVersionsResponse;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityStaticFinding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ComplexityAnalysisMapper {

    private final ComplexityProperties properties;
    private final ComplexityAnalysisJsonSupport jsonSupport;

    public ComplexityAnalysisRequestResponse toRequestResponse(ComplexityAnalysis analysis, boolean reused) {
        return new ComplexityAnalysisRequestResponse(
                analysis.getAnalysisId(),
                analysis.getSubmissionId(),
                analysis.getStatus().name(),
                reused,
                properties.getPollAfterMs());
    }

    public ComplexityAnalysisSummaryResponse toSummary(ComplexityAnalysis analysis) {
        return new ComplexityAnalysisSummaryResponse(
                analysis.getAnalysisId(),
                analysis.getSubmissionId(),
                analysis.getStatus().name(),
                analysis.getResultKind() != null ? analysis.getResultKind().name() : null,
                analysis.getLanguage(),
                analysis.getRequestedAt(),
                analysis.getCompletedAt());
    }

    public ComplexityAnalysisDetailResponse toDetail(
            ComplexityAnalysis analysis,
            Boolean reusedFlag,
            List<ComplexityStaticFinding> findings) {
        ComplexityEstimateResponse time = analysis.getTimeBigO() == null ? null : new ComplexityEstimateResponse(
                analysis.getTimeExpression(),
                analysis.getTimeBigO(),
                analysis.getTimeBoundBasis(),
                analysis.getTimeConfidence() != null ? analysis.getTimeConfidence().name() : null);
        ComplexityEstimateResponse space = analysis.getSpaceBigO() == null ? null : new ComplexityEstimateResponse(
                analysis.getSpaceExpression(),
                analysis.getSpaceBigO(),
                null,
                analysis.getSpaceConfidence() != null ? analysis.getSpaceConfidence().name() : null);

        Map<String, String> variableMap = jsonSupport.readStringMap(analysis.getVariableDefinitionsJson());
        List<ComplexityVariableResponse> variables = variableMap.entrySet().stream()
                .map(e -> new ComplexityVariableResponse(e.getKey(), e.getValue()))
                .toList();

        List<String> staticEvidence = findings.stream()
                .map(f -> f.getCategory() + ": " + f.getSummary())
                .limit(20)
                .toList();

        ComplexityVersionsResponse versions = new ComplexityVersionsResponse(
                analysis.getAnalyzerVersion(),
                null,
                analysis.getConfidenceModelVersion(),
                analysis.getKnowledgeBaseVersion(),
                null,
                null,
                null,
                null);

        return new ComplexityAnalysisDetailResponse(
                analysis.getAnalysisId(),
                analysis.getSubmissionId(),
                analysis.getStatus().name(),
                analysis.getResultKind() != null ? analysis.getResultKind().name() : null,
                analysis.getLanguage(),
                analysis.getRequestedAt(),
                analysis.getCompletedAt(),
                time,
                space,
                variables,
                new ComplexityEvidenceResponse(staticEvidence, Collections.emptyList()),
                jsonSupport.readStringList(analysis.getLimitationsJson()),
                versions,
                reusedFlag);
    }
}
