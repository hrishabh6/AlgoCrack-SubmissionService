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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ComplexityHybridConfidenceModelTest {

    private final ComplexityHybridConfidenceModel model = new ComplexityHybridConfidenceModel();

    @Test
    void hybridHighRequiresStrongStaticAndDynamicEvidence() {
        StaticAnalysisResult medium = staticTime(ComplexityConfidence.MEDIUM);
        DynamicGrowthInferenceResult dynamic = new DynamicGrowthInferenceResult(
                GrowthCandidateFamily.O_N, "DYNAMIC_FIT", List.of("FIT:O_N"), false);
        assertEquals(
                ComplexityConfidence.MEDIUM,
                model.timeConfidence(new ComplexityHybridConfidenceModel.ConfidenceInputs(
                        ComplexityResultKind.HYBRID, medium, dynamic, 5, 5, true, false)));

        StaticAnalysisResult high = staticTime(ComplexityConfidence.HIGH);
        assertEquals(
                ComplexityConfidence.HIGH,
                model.timeConfidence(new ComplexityHybridConfidenceModel.ConfidenceInputs(
                        ComplexityResultKind.HYBRID, high, dynamic, 5, 5, true, false)));
    }

    @Test
    void hybridConflictCapsLowNotHigh() {
        StaticAnalysisResult stat = staticTime(ComplexityConfidence.HIGH);
        DynamicGrowthInferenceResult dynamic = DynamicGrowthInferenceResult.inconclusive("BENCHMARK_NOISY", List.of());
        var confidence = model.timeConfidence(new ComplexityHybridConfidenceModel.ConfidenceInputs(
                ComplexityResultKind.HYBRID, stat, dynamic, 5, 5, false, false));
        assertEquals(ComplexityConfidence.LOW, confidence);
    }

    @Test
    void empiricalOnlyNeverHigh() {
        StaticAnalysisResult stat = staticUnknown();
        DynamicGrowthInferenceResult dynamic = new DynamicGrowthInferenceResult(
                GrowthCandidateFamily.O_N, "DYNAMIC_FIT", List.of(), false);
        var confidence = model.timeConfidence(new ComplexityHybridConfidenceModel.ConfidenceInputs(
                ComplexityResultKind.EMPIRICAL_ONLY, stat, dynamic, 6, 6, false, false));
        assertNotEquals(ComplexityConfidence.HIGH, confidence);
        assertEquals(ComplexityConfidence.MEDIUM, confidence);
    }

    private static StaticAnalysisResult staticTime(ComplexityConfidence confidence) {
        return new StaticAnalysisResult(
                ComplexityResultKind.STATIC_ONLY,
                new ComplexityExpr.Variable("n"),
                ComplexityBoundBasis.WORST_CASE,
                confidence,
                null,
                null,
                java.util.Map.of("n", "size"),
                List.of(),
                List.of(),
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
                java.util.Map.of(),
                List.of(),
                List.of("OPAQUE_CALLS"),
                List.of(),
                null);
    }
}
