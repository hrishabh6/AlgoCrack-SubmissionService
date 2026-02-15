package com.hrishabh.algocracksubmissionservice.judging.validator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of validating structural constraints on output.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private boolean passed;
    private String reason;

    public static ValidationResult passed() {
        return ValidationResult.builder()
                .passed(true)
                .build();
    }

    public static ValidationResult failed(String reason) {
        return ValidationResult.builder()
                .passed(false)
                .reason(reason)
                .build();
    }
}
