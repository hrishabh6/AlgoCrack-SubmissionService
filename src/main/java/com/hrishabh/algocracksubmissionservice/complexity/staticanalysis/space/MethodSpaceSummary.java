package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.space;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;

import java.util.List;

/**
 * Interprocedural auxiliary-space summary for a user method (Batch 4).
 * Returned allocation is tracked separately from peak in-method auxiliary memory.
 */
public record MethodSpaceSummary(
        ComplexityExpr auxiliaryPeak,
        ComplexityExpr returnedAllocation,
        boolean incomplete) {

    public static MethodSpaceSummary empty() {
        return new MethodSpaceSummary(ComplexityExpr.one(), ComplexityExpr.one(), false);
    }

    public MethodSpaceSummary mergeIncomplete(boolean flag) {
        return flag ? new MethodSpaceSummary(auxiliaryPeak, returnedAllocation, true) : this;
    }

    public ComplexityExpr callerOwnedEffect(boolean passThroughAsOutput) {
        if (passThroughAsOutput) {
            return auxiliaryPeak;
        }
        return ComplexityExprSimplifier.simplify(
                new ComplexityExpr.Sum(List.of(auxiliaryPeak, returnedAllocation)));
    }
}
