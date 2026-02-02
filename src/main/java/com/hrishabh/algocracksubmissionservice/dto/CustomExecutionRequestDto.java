package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;
import java.util.List;

/**
 * Request DTO for custom test case execution.
 * Unlike official submissions, custom test cases are execution-only (no judging).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomExecutionRequestDto {

    private Long questionId;
    private String language;
    private String code;
    private List<CustomTestCase> testCases;

    /**
     * User-provided test case input.
     * No expected output - user will visually inspect the result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomTestCase {
        private String input;  // JSON string of input parameters
    }
}
