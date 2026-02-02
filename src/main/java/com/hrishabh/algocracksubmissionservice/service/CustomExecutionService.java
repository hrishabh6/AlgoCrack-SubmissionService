package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocrackentityservice.models.Language;
import com.hrishabh.algocrackentityservice.models.QuestionMetadata;
import com.hrishabh.algocracksubmissionservice.dto.*;
import com.hrishabh.algocracksubmissionservice.repository.QuestionMetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for executing code with custom (user-provided) test cases.
 * 
 * Key differences from official submissions:
 * - No database persistence
 * - No judging/verdict - returns raw output
 * - No statistics updates
 * - Synchronous execution (no WebSocket updates)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomExecutionService {

    private final QuestionMetadataRepository metadataRepository;
    private final CodeExecutionClientService cxeClient;
    private final ObjectMapper objectMapper;

    /**
     * Execute code with custom test cases.
     * Returns raw output without any pass/fail verdict.
     */
    public CustomExecutionResponseDto executeCustomTests(CustomExecutionRequestDto request) {
        log.info("Executing custom test cases for question {} in {}",
                request.getQuestionId(), request.getLanguage());

        try {
            // 1. Validate and fetch metadata
            Language language = Language.valueOf(request.getLanguage().toUpperCase());
            QuestionMetadata metadata = metadataRepository
                    .findByQuestionIdAndLanguage(request.getQuestionId(), language)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Question metadata not found for questionId: " + request.getQuestionId()
                                    + ", language: " + language));

            // 2. Build execution request
            ExecutionRequest executionRequest = buildExecutionRequest(request, metadata);

            // 3. Submit to CXE
            log.debug("Submitting custom execution to CXE: {}", executionRequest.getSubmissionId());
            ExecutionResponse cxeResponse = cxeClient.submitCode(executionRequest);

            // 4. Poll for completion
            SubmissionStatusDto result = pollForCompletion(cxeResponse.getSubmissionId());

            // 5. Map to response (no judging)
            return mapToResponse(request, result);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Custom execution failed: {}", e.getMessage(), e);
            return CustomExecutionResponseDto.builder()
                    .status("RUNTIME_ERROR")
                    .compilationOutput(null)
                    .results(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Build execution request for CXE from custom test case request.
     */
    private ExecutionRequest buildExecutionRequest(CustomExecutionRequestDto request,
            QuestionMetadata metadata) {
        // Generate a temporary submission ID for CXE tracking
        String submissionId = "custom-" + UUID.randomUUID().toString();

        // Build parameters from metadata
        List<String> paramNames = metadata.getParamNames();
        List<String> paramTypes = metadata.getParamTypes();

        List<ExecutionRequest.Parameter> parameters = new ArrayList<>();
        for (int i = 0; i < paramNames.size() && i < paramTypes.size(); i++) {
            parameters.add(ExecutionRequest.Parameter.builder()
                    .name(paramNames.get(i))
                    .type(paramTypes.get(i))
                    .build());
        }

        // Build metadata DTO
        ExecutionRequest.QuestionMetadata metadataDto = ExecutionRequest.QuestionMetadata.builder()
                .fullyQualifiedPackageName("com.algocrack.solution.q" + request.getQuestionId())
                .functionName(metadata.getFunctionName())
                .returnType(metadata.getReturnType())
                .parameters(parameters)
                .customDataStructureNames(new ArrayList<>())
                .build();

        // Convert custom test cases to CXE format
        List<Map<String, Object>> testCaseMaps = request.getTestCases().stream()
                .map(this::convertCustomTestCase)
                .collect(Collectors.toList());

        return ExecutionRequest.builder()
                .submissionId(submissionId)
                .userId(0L) // No user association for custom runs
                .questionId(request.getQuestionId())
                .language(request.getLanguage())
                .code(request.getCode())
                .metadata(metadataDto)
                .testCases(testCaseMaps)
                .build();
    }

    /**
     * Convert custom test case to CXE format.
     * Custom test cases only have input, no expected output.
     */
    private Map<String, Object> convertCustomTestCase(CustomExecutionRequestDto.CustomTestCase testCase) {
        Map<String, Object> map = new HashMap<>();
        try {
            Object input = objectMapper.readValue(testCase.getInput(), Object.class);
            map.put("input", input);
            // No expectedOutput for custom cases - CXE should handle this gracefully
            map.put("expectedOutput", null);
        } catch (Exception e) {
            log.warn("Failed to parse custom test case input: {}", e.getMessage());
            map.put("input", testCase.getInput());
            map.put("expectedOutput", null);
        }
        return map;
    }

    /**
     * Poll CXE for completion.
     */
    private SubmissionStatusDto pollForCompletion(String submissionId) {
        int maxAttempts = 60; // 30 seconds max
        int pollIntervalMs = 500;

        for (int i = 0; i < maxAttempts; i++) {
            SubmissionStatusDto status = cxeClient.getStatus(submissionId);

            if ("COMPLETED".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) {
                return cxeClient.getResults(submissionId);
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            }
        }

        throw new RuntimeException("Execution timeout after " + (maxAttempts * pollIntervalMs / 1000) + " seconds");
    }

    /**
     * Map CXE result to custom execution response.
     * Key difference: NO verdict/judging - just raw output.
     */
    private CustomExecutionResponseDto mapToResponse(CustomExecutionRequestDto request,
            SubmissionStatusDto result) {
        String status = determineStatus(result);

        List<CustomExecutionResponseDto.TestCaseOutput> outputs = new ArrayList<>();

        if (result.getTestCaseResults() != null) {
            for (int i = 0; i < result.getTestCaseResults().size(); i++) {
                SubmissionStatusDto.TestCaseResult tcResult = result.getTestCaseResults().get(i);
                String inputStr = i < request.getTestCases().size()
                        ? request.getTestCases().get(i).getInput()
                        : "";

                outputs.add(CustomExecutionResponseDto.TestCaseOutput.builder()
                        .index(i)
                        .input(inputStr)
                        .output(tcResult.getActualOutput()) // Raw output, no comparison
                        .error(tcResult.getError())
                        .executionTimeMs(tcResult.getExecutionTimeMs())
                        .memoryKb(result.getMemoryKb() != null ? result.getMemoryKb().longValue() : null)
                        .build());
            }
        }

        return CustomExecutionResponseDto.builder()
                .status(status)
                .compilationOutput(result.getCompilationOutput())
                .results(outputs)
                .build();
    }

    /**
     * Determine overall status from CXE result.
     */
    private String determineStatus(SubmissionStatusDto result) {
        if (result.getCompilationOutput() != null && !result.getCompilationOutput().isEmpty()) {
            // Check if it looks like an error
            if (result.getCompilationOutput().toLowerCase().contains("error")) {
                return "COMPILATION_ERROR";
            }
        }

        if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
            if (result.getErrorMessage().toLowerCase().contains("timeout")) {
                return "TIMEOUT";
            }
            return "RUNTIME_ERROR";
        }

        return "EXECUTED";
    }
}
