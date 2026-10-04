package com.hrishabh.algocracksubmissionservice.complexity.reconciliation;

import com.hrishabh.algocracksubmissionservice.complexity.inference.DynamicGrowthInferenceResult;
import com.hrishabh.algocracksubmissionservice.complexity.inference.GrowthCandidateFamily;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;

import java.util.List;

public record ReconciliationInput(
        StaticAnalysisResult staticResult,
        DynamicGrowthInferenceResult dynamicResult,
        GrowthCandidateFamily dynamicFamily,
        boolean benchmarkAttempted,
        boolean benchmarkInfrastructureFailure,
        List<String> limitations) {
}
