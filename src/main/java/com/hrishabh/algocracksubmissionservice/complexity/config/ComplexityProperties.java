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

    private long workerPollMs = 2000L;
}
