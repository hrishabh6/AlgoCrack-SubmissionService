package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

import java.util.List;

/**
 * Request DTO for /run endpoint.
 * Synchronous "Run Code" similar to LeetCode's Run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunRequestDto {

    /**
     * Programming language (java, python, cpp, javascript)
     */
    private String language;

    /**
     * User's code to execute
     */
    private String code;

    /**
     * Question ID being tested
     */
    private Long questionId;

    /**
     * Custom testcases provided by user.
     * If null/empty, uses DEFAULT testcases from DB.
     */
    private List<CustomTestCase> customTestCases;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomTestCase {
        /**
         * Input as JSON string
         */
        private String input;
    }
}
