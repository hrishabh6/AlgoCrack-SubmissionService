package com.hrishabh.algocracksubmissionservice.complexity.reconciliation;

import com.hrishabh.algocracksubmissionservice.complexity.inference.DynamicGrowthInferenceResult;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ComplexityHybridConfidenceModel {

    public record ConfidenceInputs(
            ComplexityResultKind resultKind,
            StaticAnalysisResult staticResult,
            DynamicGrowthInferenceResult dynamicResult,
            int usableBenchmarkPoints,
            int filteredBenchmarkPoints,
            boolean staticDynamicAgree,
            boolean benchmarkInfrastructureFailure) {
    }

    public ComplexityConfidence timeConfidence(ConfidenceInputs inputs) {
        ComplexityResultKind resultKind = inputs.resultKind();
        if (resultKind == ComplexityResultKind.INCONCLUSIVE || resultKind == ComplexityResultKind.UNSUPPORTED) {
            return null;
        }
        if (resultKind == ComplexityResultKind.EMPIRICAL_ONLY) {
            return inputs.usableBenchmarkPoints() >= 5 && dynamicCredible(inputs.dynamicResult())
                    ? ComplexityConfidence.MEDIUM
                    : ComplexityConfidence.LOW;
        }
        if (resultKind == ComplexityResultKind.STATIC_ONLY) {
            return inputs.staticResult().timeConfidence();
        }
        if (resultKind == ComplexityResultKind.HYBRID) {
            if (!inputs.staticDynamicAgree()) {
                return ComplexityConfidence.LOW;
            }
            if (highEvidence(inputs)) {
                return ComplexityConfidence.HIGH;
            }
            if (inputs.staticResult().timeConfidence() == ComplexityConfidence.LOW) {
                return ComplexityConfidence.LOW;
            }
            return ComplexityConfidence.MEDIUM;
        }
        return inputs.staticResult().timeConfidence();
    }

    private boolean highEvidence(ConfidenceInputs inputs) {
        StaticAnalysisResult stat = inputs.staticResult();
        if (!stat.hasResponsibleTimeEstimate()) {
            return false;
        }
        if (stat.timeConfidence() != ComplexityConfidence.HIGH) {
            return false;
        }
        if (stat.limitations() != null && stat.limitations().stream().anyMatch(this::majorOpaqueLimitation)) {
            return false;
        }
        if (inputs.usableBenchmarkPoints() < 4 || inputs.filteredBenchmarkPoints() < 4) {
            return false;
        }
        if (!dynamicCredible(inputs.dynamicResult())) {
            return false;
        }
        if (inputs.dynamicResult().evidenceCodes().contains("AMBIGUOUS_FAMILY")) {
            return false;
        }
        return inputs.staticDynamicAgree();
    }

    private boolean dynamicCredible(DynamicGrowthInferenceResult dynamic) {
        return dynamic != null
                && !dynamic.multiDimensionalInconclusive()
                && dynamic.family() != null
                && dynamic.family().bigOLabel().isPresent()
                && "DYNAMIC_FIT".equals(dynamic.reasonCode());
    }

    private boolean majorOpaqueLimitation(String code) {
        return code != null && (code.startsWith("OPAQUE") || code.contains("UNSUPPORTED") || code.contains("UNRESOLVED"));
    }

    public List<String> reasonCodes(ConfidenceInputs inputs) {
        List<String> codes = new java.util.ArrayList<>();
        if (inputs.staticResult().limitations() != null) {
            codes.addAll(inputs.staticResult().limitations());
        }
        if (inputs.usableBenchmarkPoints() >= 4) {
            codes.add("BENCHMARK_POINTS_SUFFICIENT");
        }
        if (inputs.staticDynamicAgree()) {
            codes.add("STATIC_DYNAMIC_AGREE");
        }
        if (inputs.dynamicResult() != null && inputs.dynamicResult().evidenceCodes() != null) {
            codes.addAll(inputs.dynamicResult().evidenceCodes());
        }
        return codes;
    }
}
