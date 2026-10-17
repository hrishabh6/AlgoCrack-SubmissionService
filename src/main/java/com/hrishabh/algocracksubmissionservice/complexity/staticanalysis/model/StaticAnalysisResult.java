package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;

import java.util.ArrayList;
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

    public StaticAnalysisResult withLimitation(String code) {
        if (code == null || (limitations != null && limitations.contains(code))) {
            return this;
        }
        List<String> next = new ArrayList<>();
        if (limitations != null) {
            next.addAll(limitations);
        }
        next.add(code);
        return new StaticAnalysisResult(
                resultKind,
                timeExpression,
                timeBoundBasis,
                timeConfidence,
                spaceExpression,
                spaceConfidence,
                variables,
                findings,
                next,
                evidenceSummaries,
                errorCode,
                reasonCodes,
                timeCompleteness,
                spaceCompleteness,
                diagnosticTimeExpression);
    }

    public boolean hasAuthoritativeStaticSpace() {
        return spaceCompleteness == AnalysisDimensionCompleteness.COMPLETE
                && spaceExpression != null
                && !(spaceExpression instanceof ComplexityExpr.Unknown);
    }
}
