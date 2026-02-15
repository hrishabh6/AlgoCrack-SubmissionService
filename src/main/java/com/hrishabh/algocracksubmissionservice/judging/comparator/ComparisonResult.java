package com.hrishabh.algocracksubmissionservice.judging.comparator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of comparing two normalized outputs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResult {

    private boolean passed;
    private String reason;

    public static ComparisonResult passed() {
        return ComparisonResult.builder()
                .passed(true)
                .build();
    }

    public static ComparisonResult failed(String reason) {
        return ComparisonResult.builder()
                .passed(false)
                .reason(reason)
                .build();
    }
}
