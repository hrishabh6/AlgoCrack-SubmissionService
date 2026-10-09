package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.space.MethodSpaceSummary;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared interprocedural analysis state (Batch 2).
 */
public final class InterproceduralContext {

    public enum MethodVisitState {
        UNVISITED,
        VISITING,
        COMPLETE
    }

    public final UserMethodIndex userMethods;
    public final MethodCallGraph callGraph = new MethodCallGraph();
    public final Map<String, ComplexityExpr> helperSummaryMemo = new HashMap<>();
    public final Map<String, MethodSpaceSummary> helperSpaceSummaryMemo = new HashMap<>();
    public final Map<String, ComplexityExpr> recurrenceMemo = new HashMap<>();
    public final Map<MethodIdentity, MethodVisitState> visitStates = new HashMap<>();

    public InterproceduralContext(UserMethodIndex userMethods) {
        this.userMethods = userMethods;
    }
}
