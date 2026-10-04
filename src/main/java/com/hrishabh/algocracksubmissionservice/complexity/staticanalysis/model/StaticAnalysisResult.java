package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;

import java.util.List;
import java.util.Map;

public record StaticAnalysisResult(
        ComplexityResultKind resultKind,
        ComplexityExpr timeExpression,
        ComplexityBoundBasis timeBoundBasis,
        ComplexityConfidence timeConfidence,
        ComplexityExpr spaceExpression,
        ComplexityConfidence spaceConfidence,
        Map<String, String> variables,
        List<StaticFindingDraft> findings,
        List<String> limitations,
        List<String> evidenceSummaries,
        String errorCode) {

    public boolean hasResponsibleTimeEstimate() {
        return timeExpression != null && !(timeExpression instanceof ComplexityExpr.Unknown);
    }
}
