package com.hrishabh.algocracksubmissionservice.complexity.inference;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class MeasurementPolicy {

    private final int minUsableSizePoints;
    private final long minUsefulDurationNs;
    private final double maxRelativeMad;
    private final double maxNormalizedSpread;
    private final double minWinnerMargin;

    public MeasurementPolicy(ComplexityProperties properties) {
        ComplexityProperties.Measurement measurement = properties.getMeasurement();
        this.minUsableSizePoints = measurement.getMinUsableSizePoints();
        this.minUsefulDurationNs = measurement.getMinUsefulDurationNs();
        this.maxRelativeMad = measurement.getMaxRelativeMad();
        this.maxNormalizedSpread = measurement.getMaxNormalizedSpread();
        this.minWinnerMargin = measurement.getMinWinnerMargin();
    }
}
