package com.hrishabh.algocracksubmissionservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocrackentityservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.TestCaseOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to validate execution results against expected outputs.
 * 
 * In v2, expected outputs come from the oracle execution, not stored TestCase
 * data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Validate execution results against oracle outputs.
     * 
     * @param actualResults     User code execution results
     * @param oracleOutputs     Expected outputs from oracle execution
     * @param compilationOutput Compilation output (to detect compile errors)
     * @return The verdict for the submission
     */
    public SubmissionVerdict validateResults(
            List<SubmissionStatusDto.TestCaseResult> actualResults,
            List<TestCaseOutput> oracleOutputs,
            String compilationOutput) {
        // Check for compilation error
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

            // Get expected output from oracle
            if (i >= oracleOutputs.size()) {
                log.warn("Oracle output index {} out of bounds", i);
                continue;
            }

            String expectedOutput = oracleOutputs.get(i).getOutput();

            // Compare outputs
            if (!outputsMatch(actual.getActualOutput(), expectedOutput)) {
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
    public boolean outputsMatch(String actual, String expected) {
        System.out.println("[ResultValidationService] outputsMatch() called");
        System.out.println("    actual (raw):   \"" + actual + "\"");
        System.out.println("    expected (raw): \"" + expected + "\"");

        if (actual == null || expected == null) {
            boolean result = actual == expected;
            System.out.println("    Null check: result=" + result);
            return result;
        }

        // Normalize whitespace
        actual = actual.trim();
        expected = expected.trim();
        System.out.println("    actual (trimmed):   \"" + actual + "\"");
        System.out.println("    expected (trimmed): \"" + expected + "\"");

        // Direct string match
        if (actual.equals(expected)) {
            System.out.println("    Direct string match: TRUE");
            return true;
        }
        System.out.println("    Direct string match: FALSE, trying JSON comparison...");

        // Try JSON comparison for arrays/objects
        try {
            JsonNode actualNode = objectMapper.readTree(actual);
            JsonNode expectedNode = objectMapper.readTree(expected);
            boolean jsonMatch = actualNode.equals(expectedNode);
            System.out.println("    JSON comparison result: " + jsonMatch);
            return jsonMatch;
        } catch (Exception e) {
            // Not valid JSON, fall back to string comparison
            log.debug("JSON parsing failed, using string comparison");
            System.out.println("    JSON parse failed: " + e.getMessage());
            System.out.println("    Falling back to string comparison: " + actual.equals(expected));
            return actual.equals(expected);
        }
    }

    /**
     * Count passed test cases.
     */
    public int countPassed(List<SubmissionStatusDto.TestCaseResult> results) {
        if (results == null)
            return 0;
        return (int) results.stream()
                .filter(r -> Boolean.TRUE.equals(r.getPassed()))
                .count();
    }

    private boolean containsCompilationError(String output) {
        if (output == null)
            return false;
        String lower = output.toLowerCase();
        // Actual javac/python compilation errors
        return lower.contains("error:")
                || lower.contains("cannot find symbol")
                || lower.contains("syntax error")
                || lower.contains("compilation failed");
    }
}
