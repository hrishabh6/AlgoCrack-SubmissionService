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
        String note,
        /**
         * Required argument count at call site, or -1 when any count is safe for this entry.
         */
        int requiredArgumentCount) {
}
