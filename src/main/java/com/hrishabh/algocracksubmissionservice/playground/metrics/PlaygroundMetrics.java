package com.hrishabh.algocracksubmissionservice.playground.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PlaygroundMetrics {

    private final MeterRegistry registry;

    public PlaygroundMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRun(String language, String outcome) {
        registry.counter(
                "playground_runs_total",
                "language", normalizeTag(language),
                "outcome", normalizeTag(outcome)).increment();
    }

    public void recordRunRejection(String reason) {
        registry.counter(
                "playground_run_rejections_total",
                "reason", normalizeTag(reason)).increment();
    }

    private static String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toUpperCase();
    }
}
