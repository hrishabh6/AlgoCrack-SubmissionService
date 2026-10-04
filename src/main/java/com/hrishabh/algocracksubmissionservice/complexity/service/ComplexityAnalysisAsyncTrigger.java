package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "complexity", name = "static-analysis-enabled", havingValue = "true")
@RequiredArgsConstructor
public class ComplexityAnalysisAsyncTrigger {

    private final ComplexityStaticAnalysisPipeline pipeline;
    private final ComplexityProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisRequested(ComplexityAnalysisRequestedEvent event) {
        if (!properties.isStaticAnalysisEnabled()) {
            return;
        }
        pipeline.processQueuedAnalysis(event.analysisId());
    }

    public record ComplexityAnalysisRequestedEvent(String analysisId) {
    }
}
