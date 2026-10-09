package com.hrishabh.algocracksubmissionservice.complexity.reconciliation;

import com.hrishabh.algocracksubmissionservice.complexity.inference.DynamicGrowthInferenceResult;
import com.hrishabh.algocracksubmissionservice.complexity.inference.GrowthCandidateFamily;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ComplexityReconciliationEngine {

    public ReconciliationOutcome reconcile(ReconciliationInput input) {
        List<String> limitations = new ArrayList<>(input.limitations());
        List<String> reasons = new ArrayList<>();
        StaticAnalysisResult stat = input.staticResult();
        DynamicGrowthInferenceResult dynamic = input.dynamicResult();

        boolean authoritativeStatic = stat.hasAuthoritativeStaticTime();
        String staticBigO = authoritativeStatic && stat.timeExpression() != null
                ? ComplexityExprSimplifier.toBigOString(stat.timeExpression())
                : null;
        Optional<GrowthCandidateFamily> staticFamily = GrowthCandidateFamily.fromStaticBigO(staticBigO);
        GrowthCandidateFamily dynamicFamily = input.dynamicFamily();
        boolean dynamicKnown = dynamicFamily != null && dynamicFamily != GrowthCandidateFamily.INCONCLUSIVE;

        if (dynamic != null && dynamic.multiDimensionalInconclusive()) {
            limitations.add("MULTI_DIMENSIONAL_INCONCLUSIVE");
            return boundedStaticOutcome(stat, limitations, ComplexityConfidence.LOW);
        }

        if (!input.benchmarkAttempted()) {
            return staticOnlyOutcome(stat, limitations);
        }

        if (input.benchmarkInfrastructureFailure()) {
            limitations.add("PROFILE_INFRASTRUCTURE_UNAVAILABLE");
            return boundedStaticOutcome(stat, limitations, stat.timeConfidence());
        }

        if (!dynamicKnown) {
            if (authoritativeStatic && staticFamily.isPresent()) {
                limitations.add(dynamic != null ? dynamic.reasonCode() : "BENCHMARK_INSUFFICIENT_POINTS");
                return boundedStaticOutcome(stat, limitations, stat.timeConfidence());
            }
            reasons.add("BENCHMARK_INSUFFICIENT_POINTS");
            return inconclusiveOutcome(stat, limitations, reasons);
        }

        if (!authoritativeStatic || staticFamily.isEmpty()) {
            reasons.add("EMPIRICAL_DYNAMIC");
            return new ReconciliationOutcome(
                    ComplexityResultKind.EMPIRICAL_ONLY,
                    ComplexityConfidence.MEDIUM,
                    dynamicFamily.bigOLabel().orElse(null),
                    dynamicFamily.bigOLabel().orElse(null),
                    reasons,
                    limitations);
        }

        if (staticFamily.get() == dynamicFamily) {
            reasons.add("STATIC_DYNAMIC_AGREE");
            return new ReconciliationOutcome(
                    ComplexityResultKind.HYBRID,
                    null,
                    staticBigO,
                    ComplexityExprSimplifier.toExpressionString(stat.timeExpression()),
                    reasons,
                    limitations);
        }

        reasons.add("STATIC_DYNAMIC_CONFLICT");
        limitations.add("STATIC_DYNAMIC_CONFLICT");
        return inconclusiveOutcome(stat, limitations, reasons);
    }

    private ReconciliationOutcome staticOnlyOutcome(StaticAnalysisResult stat, List<String> limitations) {
        ComplexityResultKind kind = stat.resultKind();
        if (kind == ComplexityResultKind.UNSUPPORTED || kind == ComplexityResultKind.INCONCLUSIVE) {
            return inconclusiveOutcome(stat, limitations, List.of("STATIC_NOT_AUTHORITATIVE"));
        }
        return new ReconciliationOutcome(
                ComplexityResultKind.STATIC_ONLY,
                stat.timeConfidence(),
                stat.timeExpression() != null ? ComplexityExprSimplifier.toBigOString(stat.timeExpression()) : null,
                stat.timeExpression() != null ? ComplexityExprSimplifier.toExpressionString(stat.timeExpression()) : null,
                List.of("STATIC_ONLY"),
                limitations);
    }

    private ReconciliationOutcome boundedStaticOutcome(
            StaticAnalysisResult stat, List<String> limitations, ComplexityConfidence confidence) {
        if (!stat.hasAuthoritativeStaticTime()) {
            return inconclusiveOutcome(stat, limitations, List.of("STATIC_WITH_BENCHMARK_LIMITATION"));
        }
        return new ReconciliationOutcome(
                ComplexityResultKind.STATIC_ONLY,
                confidence,
                ComplexityExprSimplifier.toBigOString(stat.timeExpression()),
                ComplexityExprSimplifier.toExpressionString(stat.timeExpression()),
                List.of("STATIC_WITH_BENCHMARK_LIMITATION"),
                limitations);
    }

    private ReconciliationOutcome inconclusiveOutcome(
            StaticAnalysisResult stat, List<String> limitations, List<String> reasons) {
        return new ReconciliationOutcome(
                ComplexityResultKind.INCONCLUSIVE,
                null,
                null,
                null,
                reasons,
                limitations);
    }
}
