package com.hrishabh.algocracksubmissionservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocrackentityservice.models.SubmissionVerdict;
import com.hrishabh.algocrackentityservice.models.TestCase;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to validate execution results against expected test case outputs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Validate execution results and determine verdict.
     */
    public SubmissionVerdict validateResults(
            List<SubmissionStatusDto.TestCaseResult> actualResults,
            List<TestCase> expectedTestCases,
            String compilationOutput
    ) {
        // Check for compilation error - look for actual error indicators, not just non-empty
        if (compilationOutput != null && !compilationOutput.isEmpty()
                && containsCompilationError(compilationOutput)) {
            log.info("Compilation error detected");
            return SubmissionVerdict.COMPILATION_ERROR;
        }

        if (actualResults == null || actualResults.isEmpty()) {
            log.warn("No test case results received");
            return SubmissionVerdict.INTERNAL_ERROR;
        }

        for (int i = 0; i < actualResults.size(); i++) {
            SubmissionStatusDto.TestCaseResult actual = actualResults.get(i);

            // Check for runtime error
            if (actual.getError() != null && !actual.getError().isEmpty()) {
                log.info("Runtime error on test case {}: {}", i, actual.getError());
                return SubmissionVerdict.RUNTIME_ERROR;
            }

            // Get expected output
            if (i >= expectedTestCases.size()) {
                log.warn("Test case index {} out of bounds", i);
                continue;
            }
            
            TestCase expected = expectedTestCases.get(i);

            // Compare outputs
            if (!outputsMatch(actual.getActualOutput(), expected.getExpectedOutput())) {
                log.info("Wrong answer on test case {}", i);
                return SubmissionVerdict.WRONG_ANSWER;
            }
        }

        log.info("All test cases passed");
        return SubmissionVerdict.ACCEPTED;
    }

    /**
     * Compare actual output with expected output.
     * Handles JSON comparison for complex types.
     */
    private boolean outputsMatch(String actual, String expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }

        // Normalize whitespace
        actual = actual.trim();
        expected = expected.trim();

        // Direct string match
        if (actual.equals(expected)) {
            return true;
        }

        // Try JSON comparison for arrays/objects
        try {
            JsonNode actualNode = objectMapper.readTree(actual);
            JsonNode expectedNode = objectMapper.readTree(expected);
            return actualNode.equals(expectedNode);
        } catch (Exception e) {
            // Not valid JSON, fall back to string comparison
            log.debug("JSON parsing failed, using string comparison");
            return actual.equals(expected);
        }
    }

    /**
     * Count passed test cases.
     */
    public int countPassed(List<SubmissionStatusDto.TestCaseResult> results) {
        if (results == null) return 0;
        return (int) results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getPassed()))
                .count();
    }

    private boolean containsCompilationError(String output) {
        if (output == null) return false;
        String lower = output.toLowerCase();
        // Actual javac/python compilation errors
        return lower.contains("error:")
                || lower.contains("cannot find symbol")
                || lower.contains("syntax error")
                || lower.contains("compilation failed");
    }
}
