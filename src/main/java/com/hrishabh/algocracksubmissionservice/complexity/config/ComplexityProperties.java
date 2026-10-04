package com.hrishabh.algocracksubmissionservice.complexity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "complexity")
public class ComplexityProperties {

    /**
     * When false, public complexity REST controllers are not registered.
     */
    private boolean apiEnabled = false;

    private int maxPageSize = 20;

    private long pollAfterMs = 1000L;

    /**
     * Runs deterministic static analysis worker (Phase 2). Dynamic profiling remains off.
     * Safe with multiple service replicas when {@code claimQueuedForStaticAnalysis} is used (Phase 2 hardening).
     */
    private boolean staticAnalysisEnabled = false;

    /**
     * When true (with static worker), runs benchmark + dynamic inference after static analysis.
     */
    private boolean dynamicProfilingEnabled = false;

    private long workerPollMs = 2000L;

    private long workerLeaseSeconds = 45L;

    private int cxeProfilePollMaxAttempts = 240;

    private long cxeProfilePollIntervalMs = 500L;

    private Measurement measurement = new Measurement();

    @Getter
    @Setter
    public static class Measurement {
        private int minUsableSizePoints = 4;
        private long minUsefulDurationNs = 50_000L;
        private double maxRelativeMad = 0.35;
        private double maxNormalizedSpread = 0.45;
        private double minWinnerMargin = 0.15;
    }
}
