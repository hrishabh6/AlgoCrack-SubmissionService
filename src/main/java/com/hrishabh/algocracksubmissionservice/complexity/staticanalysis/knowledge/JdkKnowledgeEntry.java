package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;

public record JdkKnowledgeEntry(
        String pattern,
        String jdkBaseline,
        ComplexityBoundBasis boundBasis,
        ComplexityExpr timeExpression,
        ComplexityExpr allocationExpression,
        String assumptions,
        String note) {
}
