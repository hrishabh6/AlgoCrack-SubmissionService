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

@Component
@ConditionalOnProperty(prefix = "complexity", name = "static-analysis-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ComplexityAnalysisWorker {

    private static final List<ComplexityProcessingStatus> ACTIVE_PIPELINE = List.of(
            ComplexityProcessingStatus.QUEUED,
            ComplexityProcessingStatus.STATIC_ANALYZING,
            ComplexityProcessingStatus.BENCHMARK_PREPARING,
            ComplexityProcessingStatus.BENCHMARK_QUEUED,
            ComplexityProcessingStatus.BENCHMARKING,
            ComplexityProcessingStatus.RECONCILING);

    private final ComplexityAnalysisRepository analysisRepository;
    private final ComplexityAnalysisOrchestrator orchestrator;
    private final ComplexityProperties properties;

    @Scheduled(fixedDelayString = "${complexity.worker-poll-ms:2000}")
    public void pollActiveAnalyses() {
        List<ComplexityAnalysis> active = analysisRepository
                .findTop10ByStatusInAndActiveSlotIsNotNullOrderByRequestedAtAsc(ACTIVE_PIPELINE);
        for (ComplexityAnalysis analysis : active) {
            try {
                orchestrator.advance(analysis.getAnalysisId());
            } catch (Exception ex) {
                log.warn("Complexity orchestration failed analysisId={}", analysis.getAnalysisId(), ex);
            }
        }
    }
}
