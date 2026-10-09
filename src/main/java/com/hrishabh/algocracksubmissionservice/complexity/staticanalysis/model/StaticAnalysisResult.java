package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;

import java.util.List;

public record StaticAnalysisResult(
        ComplexityResultKind resultKind,
        ComplexityExpr timeExpression,
        ComplexityBoundBasis timeBoundBasis,
        ComplexityConfidence timeConfidence,
        ComplexityExpr spaceExpression,
        ComplexityConfidence spaceConfidence,
        java.util.Map<String, String> variables,
        List<StaticFindingDraft> findings,
        List<String> limitations,
        List<String> evidenceSummaries,
        String errorCode,
        List<StaticAnalysisReasonCode> reasonCodes,
        AnalysisDimensionCompleteness timeCompleteness,
        AnalysisDimensionCompleteness spaceCompleteness,
        ComplexityExpr diagnosticTimeExpression) {

    public StaticAnalysisResult {
        if (reasonCodes == null) {
            reasonCodes = List.of();
        }
        if (timeCompleteness == null) {
            timeCompleteness = AnalysisDimensionCompleteness.COMPLETE;
        }
        if (spaceCompleteness == null) {
            spaceCompleteness = AnalysisDimensionCompleteness.COMPLETE;
        }
    }

    public boolean hasResponsibleTimeEstimate() {
        return hasAuthoritativeStaticTime();
    }

    /**
     * Authoritative static time bound suitable for reconciliation family extraction.
     */
    public boolean hasAuthoritativeStaticTime() {
        return resultKind == ComplexityResultKind.STATIC_ONLY
                && timeCompleteness == AnalysisDimensionCompleteness.COMPLETE
                && timeExpression != null
                && !(timeExpression instanceof ComplexityExpr.Unknown)
                && reasonCodes.isEmpty();
    }

    public boolean hasAuthoritativeStaticSpace() {
        return spaceCompleteness == AnalysisDimensionCompleteness.COMPLETE
                && spaceExpression != null
                && !(spaceExpression instanceof ComplexityExpr.Unknown);
    }
}
