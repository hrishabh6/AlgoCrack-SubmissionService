package com.hrishabh.algocracksubmissionservice.complexity.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ComplexityMetrics {

    private final MeterRegistry registry;
    private final AtomicLong queuedActive = new AtomicLong(0);
    private final AtomicLong oldestQueuedAgeSeconds = new AtomicLong(0);

    public ComplexityMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("complexity_analysis_queued_active", queuedActive, AtomicLong::get);
        registry.gauge("complexity_analysis_oldest_queued_age_seconds", oldestQueuedAgeSeconds, AtomicLong::get);
    }

    public void recordAnalysisRequest(boolean duplicateActive) {
        registry.counter(
                "complexity_analysis_requests_total",
                "duplicate", duplicateActive ? "true" : "false").increment();
    }

    public void recordCompleted(String resultKind, String staticOutcome) {
        registry.counter(
                "complexity_analysis_completions_total",
                "result_kind", normalizeTag(resultKind),
                "static_outcome", normalizeTag(staticOutcome)).increment();
    }

    public void recordCacheReuse() {
        registry.counter("complexity_analysis_cache_reuse_total").increment();
    }

    public void recordBenchmarkLimitation(String errorCode) {
        registry.counter(
                "complexity_benchmark_limitations_total",
                "error_code", normalizeErrorCode(errorCode)).increment();
    }

    public void recordInferenceOutcome(String family, String outcome) {
        registry.counter(
                "complexity_inference_outcomes_total",
                "family", normalizeTag(family),
                "outcome", normalizeTag(outcome)).increment();
    }

    public void recordBenchmarkUsability(int usable, int ineligible) {
        registry.counter("complexity_benchmark_usable_points_total").increment(usable);
        registry.counter("complexity_benchmark_ineligible_points_total").increment(ineligible);
    }

    public void recordCxeTransportError(String phase) {
        registry.counter(
                "complexity_cxe_transport_errors_total",
                "phase", normalizeTag(phase)).increment();
    }

    public void recordStageDuration(String stage, long durationMs) {
        Timer.builder("complexity_analysis_stage_duration")
                .tag("stage", normalizeTag(stage))
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordQueuedDepth(long count) {
        queuedActive.set(count);
    }

    public void recordOldestQueuedAgeSeconds(long ageSeconds) {
        oldestQueuedAgeSeconds.set(Math.max(0, ageSeconds));
    }

    private static String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toUpperCase().replace(' ', '_');
    }

    private static String normalizeErrorCode(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.length() > 64) {
            return normalized.substring(0, 64);
        }
        return normalized;
    }
}
