package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls QUEUED complexity analyses. Claiming uses {@code claimQueuedForStaticAnalysis} so multiple
 * replicas do not process the same row; only one instance wins the atomic UPDATE per analysis.
 */
@Component
@ConditionalOnProperty(prefix = "complexity", name = "static-analysis-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ComplexityAnalysisWorker {

    private final ComplexityAnalysisRepository analysisRepository;
    private final ComplexityStaticAnalysisPipeline pipeline;
    private final ComplexityProperties properties;

    @Scheduled(fixedDelayString = "${complexity.worker-poll-ms:2000}")
    public void pollQueuedAnalyses() {
        List<ComplexityAnalysis> queued = analysisRepository.findTop10ByStatusOrderByRequestedAtAsc(
                ComplexityProcessingStatus.QUEUED);
        for (ComplexityAnalysis analysis : queued) {
            if (analysis.getActiveSlot() == null) {
                continue;
            }
            try {
                pipeline.processQueuedAnalysis(analysis.getAnalysisId());
            } catch (Exception ex) {
                log.warn("Complexity static analysis failed analysisId={}", analysis.getAnalysisId(), ex);
            }
        }
    }
}
