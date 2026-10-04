package com.hrishabh.algocracksubmissionservice.complexity.reconciliation;

import com.hrishabh.algocracksubmissionservice.complexity.inference.DynamicGrowthInferenceResult;
import com.hrishabh.algocracksubmissionservice.complexity.inference.GrowthCandidateFamily;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComplexityReconciliationEngineTest {

    private final ComplexityReconciliationEngine engine = new ComplexityReconciliationEngine();

    @Test
    void hybridWhenStaticAndDynamicAgreeOnN() {
        StaticAnalysisResult stat = staticWithTime("O(n)");
        ReconciliationOutcome outcome = engine.reconcile(new ReconciliationInput(
                stat,
                new DynamicGrowthInferenceResult(GrowthCandidateFamily.O_N, "DYNAMIC_FIT", List.of(), false),
                GrowthCandidateFamily.O_N,
                true,
                false,
                List.of()));
        assertEquals(ComplexityResultKind.HYBRID, outcome.resultKind());
        assertNull(outcome.timeConfidence());
    }

    @Test
    void inconclusiveOnStaticDynamicConflict() {
        StaticAnalysisResult stat = staticWithTime("O(n)");
        ReconciliationOutcome outcome = engine.reconcile(new ReconciliationInput(
                stat,
                DynamicGrowthInferenceResult.inconclusive("BENCHMARK_NOISY", List.of()),
                GrowthCandidateFamily.O_N2,
                true,
                false,
                List.of()));
        assertEquals(ComplexityResultKind.INCONCLUSIVE, outcome.resultKind());
    }

    @Test
    void staticOnlyWhenBenchmarkUnavailable() {
        StaticAnalysisResult stat = staticWithTime("O(n)");
        ReconciliationOutcome outcome = engine.reconcile(new ReconciliationInput(
                stat,
                DynamicGrowthInferenceResult.inconclusive("PROFILE_UNAVAILABLE", List.of("PROFILE_UNAVAILABLE")),
                GrowthCandidateFamily.INCONCLUSIVE,
                true,
                false,
                List.of()));
        assertEquals(ComplexityResultKind.STATIC_ONLY, outcome.resultKind());
    }

    @Test
    void empiricalOnlyWhenStaticUnknownButDynamicKnown() {
        StaticAnalysisResult stat = staticUnknown();
        ReconciliationOutcome outcome = engine.reconcile(new ReconciliationInput(
                stat,
                new DynamicGrowthInferenceResult(GrowthCandidateFamily.O_N, "DYNAMIC_FIT", List.of(), false),
                GrowthCandidateFamily.O_N,
                true,
                false,
                List.of()));
        assertEquals(ComplexityResultKind.EMPIRICAL_ONLY, outcome.resultKind());
    }

    private static StaticAnalysisResult staticWithTime(String bigO) {
        ComplexityExpr expr = switch (bigO) {
            case "O(n)" -> new ComplexityExpr.Variable("n");
            case "O(n log n)" -> new ComplexityExpr.Product(List.of(
                    new ComplexityExpr.Variable("n"),
                    new ComplexityExpr.Log(new ComplexityExpr.Variable("n"))));
            default -> new ComplexityExpr.Unknown(bigO);
        };
        return new StaticAnalysisResult(
                ComplexityResultKind.STATIC_ONLY,
                expr,
                ComplexityBoundBasis.WORST_CASE,
                ComplexityConfidence.MEDIUM,
                null,
                null,
                Map.of("n", "input size"),
                List.of(),
                List.of("OPAQUE_CALLS"),
                List.of(),
                null);
    }

    private static StaticAnalysisResult staticUnknown() {
        return new StaticAnalysisResult(
                ComplexityResultKind.INCONCLUSIVE,
                null,
                ComplexityBoundBasis.WORST_CASE,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                List.of("OPAQUE_CALLS"),
                List.of(),
                null);
    }
}
