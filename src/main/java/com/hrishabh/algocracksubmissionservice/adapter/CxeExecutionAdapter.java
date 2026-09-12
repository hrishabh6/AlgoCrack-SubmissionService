package com.hrishabh.algocracksubmissionservice.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.models.Language;
import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionRequest;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionResponse;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.*;
import com.hrishabh.algocracksubmissionservice.logging.LoggingConstants;
import com.hrishabh.algocracksubmissionservice.logging.StructuredLogger;
import com.hrishabh.algocracksubmissionservice.service.CodeExecutionClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CXE-specific implementation of ExecutionAdapter.
 * Translates internal DTOs to CXE format and back.
 * 
 * This is the ONLY component that should depend on CXE DTOs.
 * All other services use internal DTOs via the ExecutionAdapter interface.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CxeExecutionAdapter implements ExecutionAdapter {

    private final StructuredLogger structuredLogger = new StructuredLogger(CxeExecutionAdapter.class, "SubmissionService");

    private final CodeExecutionClientService cxeClient;
    private final ProblemServiceClient problemServiceClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_POLL_ATTEMPTS = 60;
    private static final int POLL_INTERVAL_MS = 500;

    @Override
    public BatchExecutionResult execute(CodeBundle codeBundle) {
        structuredLogger.info("CXE execution started",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                LoggingConstants.OPERATION, "cxe_execute",
                LoggingConstants.EXECUTION_ID, codeBundle.getExecutionId(),
                LoggingConstants.QUESTION_ID, codeBundle.getQuestionId(),
                LoggingConstants.LANGUAGE, codeBundle.getLanguage(),
                LoggingConstants.USER_ID, codeBundle.getUserId(),
                LoggingConstants.CODE_LENGTH, codeBundle.getCode() != null ? codeBundle.getCode().length() : 0,
                LoggingConstants.TESTCASE_COUNT,
                codeBundle.getTestcases() != null ? codeBundle.getTestcases().size() : 0,
                "intent", codeBundle.getIntent());

        try {
            // 1. Fetch question metadata if not provided
            CodeBundle.QuestionMetadataBundle metadataBundle = codeBundle.getMetadata();
            if (metadataBundle == null) {
                metadataBundle = fetchMetadata(codeBundle.getQuestionId(), codeBundle.getLanguage());
                codeBundle.setMetadata(metadataBundle);
            }

            // 2. Translate internal DTO → CXE DTO
            ExecutionRequest cxeRequest = translateToRequest(codeBundle);

            structuredLogger.debug("CXE request built",
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                    LoggingConstants.OPERATION, "cxe_request_built",
                    LoggingConstants.SUBMISSION_ID, cxeRequest.getSubmissionId(),
                    LoggingConstants.USER_ID, cxeRequest.getUserId(),
                    LoggingConstants.QUESTION_ID, cxeRequest.getQuestionId(),
                    LoggingConstants.LANGUAGE, cxeRequest.getLanguage(),
                    LoggingConstants.CODE_LENGTH, cxeRequest.getCode() != null ? cxeRequest.getCode().length() : 0,
                    LoggingConstants.TESTCASE_COUNT,
                    cxeRequest.getTestCases() != null ? cxeRequest.getTestCases().size() : 0,
                    "function_name", cxeRequest.getMetadata() != null ? cxeRequest.getMetadata().getFunctionName() : null,
                    "return_type", cxeRequest.getMetadata() != null ? cxeRequest.getMetadata().getReturnType() : null);

            // 3. Submit to CXE
            ExecutionResponse response = cxeClient.submitCode(cxeRequest);

            structuredLogger.info("CXE submission accepted",
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXTERNAL_CALL,
                    LoggingConstants.OPERATION, "cxe_submit",
                    LoggingConstants.SUBMISSION_ID, response.getSubmissionId(),
                    LoggingConstants.EXECUTION_ID, codeBundle.getExecutionId(),
                    LoggingConstants.STATUS, response.getStatus(),
                    LoggingConstants.QUEUE_POSITION, response.getQueuePosition());

            // 4. Poll for completion
            SubmissionStatusDto status = pollForCompletion(response.getSubmissionId());

            // 5. Translate CXE DTO → internal DTO
            BatchExecutionResult result = translateToResult(status);

            structuredLogger.info("CXE execution completed",
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                    LoggingConstants.OPERATION, "cxe_execute",
                    LoggingConstants.SUBMISSION_ID, status.getSubmissionId(),
                    LoggingConstants.EXECUTION_ID, codeBundle.getExecutionId(),
                    LoggingConstants.STATUS, result.getStatus(),
                    LoggingConstants.VERDICT, status.getVerdict(),
                    LoggingConstants.RUNTIME_MS, status.getRuntimeMs(),
                    LoggingConstants.MEMORY_KB, status.getMemoryKb(),
                    LoggingConstants.WORKER_ID, status.getWorkerId(),
                    LoggingConstants.OUTPUT_COUNT, result.getOutputs() != null ? result.getOutputs().size() : 0,
                    "has_error", status.getErrorMessage() != null,
                    "has_compilation_output", status.getCompilationOutput() != null);

            return result;

        } catch (Exception e) {
            structuredLogger.error("CXE execution failed", e,
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                    LoggingConstants.TYPE, "Error",
                    LoggingConstants.OPERATION, "cxe_execute",
                    LoggingConstants.EXECUTION_ID, codeBundle.getExecutionId(),
                    LoggingConstants.QUESTION_ID, codeBundle.getQuestionId(),
                    LoggingConstants.LANGUAGE, codeBundle.getLanguage());
            return BatchExecutionResult.builder()
                    .status(BatchExecutionResult.ExecutionStatus.INTERNAL_ERROR)
                    .errorMessage(e.getMessage())
                    .outputs(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Fetch question metadata via ProblemService API.
     */
    private CodeBundle.QuestionMetadataBundle fetchMetadata(Long questionId, Language language) {
        QuestionMetadataApiDto metadata = problemServiceClient.getMetadata(questionId, language.name());
        if (metadata == null) {
            throw new IllegalArgumentException(
                    "Question metadata not found for questionId: " + questionId + ", language: " + language);
        }

        List<CodeBundle.Parameter> parameters = new ArrayList<>();
        List<String> paramNames = metadata.getParamNames();
        List<String> paramTypes = metadata.getParamTypes();

        for (int i = 0; i < paramNames.size() && i < paramTypes.size(); i++) {
            parameters.add(CodeBundle.Parameter.builder()
                    .name(paramNames.get(i))
                    .type(paramTypes.get(i))
                    .build());
        }

        return CodeBundle.QuestionMetadataBundle.builder()
                .fullyQualifiedPackageName("com.algocrack.solution.q" + questionId)
                .functionName(metadata.getFunctionName())
                .returnType(metadata.getReturnType())
                .parameters(parameters)
                .customDataStructureNames(new ArrayList<>())
                .mutationTarget(metadata.getMutationTarget())
                .serializationStrategy(metadata.getSerializationStrategy())
                .questionType(metadata.getQuestionType())
                .build();
    }

    /**
     * Translate internal CodeBundle to CXE ExecutionRequest.
     */
    private ExecutionRequest translateToRequest(CodeBundle bundle) {
        // Convert metadata
        ExecutionRequest.QuestionMetadata cxeMetadata = ExecutionRequest.QuestionMetadata.builder()
                .fullyQualifiedPackageName(bundle.getMetadata().getFullyQualifiedPackageName())
                .functionName(bundle.getMetadata().getFunctionName())
                .returnType(bundle.getMetadata().getReturnType())
                .parameters(bundle.getMetadata().getParameters().stream()
                        .map(p -> ExecutionRequest.Parameter.builder()
                                .name(p.getName())
                                .type(p.getType())
                                .build())
                        .collect(Collectors.toList()))
                .customDataStructureNames(bundle.getMetadata().getCustomDataStructureNames())
                .mutationTarget(bundle.getMetadata().getMutationTarget())
                .serializationStrategy(bundle.getMetadata().getSerializationStrategy())
                .questionType(bundle.getMetadata().getQuestionType())
                .build();

        // Convert testcases to CXE format (List<Map<String, Object>>)
        List<Map<String, Object>> testCaseMaps = bundle.getTestcases().stream()
                .map(this::convertTestCaseToMap)
                .collect(Collectors.toList());

        return ExecutionRequest.builder()
                .submissionId(bundle.getExecutionId())
                .userId(bundle.getUserId() != null ? bundle.getUserId() : "ANONYMOUS")
                .questionId(bundle.getQuestionId())
                .language(bundle.getLanguage().name())
                .code(bundle.getCode())
                .metadata(cxeMetadata)
                .testCases(testCaseMaps)
                .build();
    }

    /**
     * Convert internal TestCaseInput to CXE map format.
     */
    private Map<String, Object> convertTestCaseToMap(TestCaseInput testCase) {
        Map<String, Object> map = new HashMap<>();
        try {
            Object input = objectMapper.readValue(testCase.getInput(), Object.class);
            map.put("input", input);
            // No expectedOutput - oracle execution computes this
            map.put("expectedOutput", null);
        } catch (Exception e) {
            log.warn("Failed to parse testcase input as JSON: {}", e.getMessage());
            map.put("input", testCase.getInput());
            map.put("expectedOutput", null);
        }
        return map;
    }

    /**
     * Poll CXE for completion.
     */
    private SubmissionStatusDto pollForCompletion(String submissionId) {
        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            SubmissionStatusDto status = cxeClient.getStatus(submissionId);

            if ("COMPLETED".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) {
                return cxeClient.getResults(submissionId);
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            }
        }

        throw new RuntimeException(
                "Execution timeout after " + (MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS / 1000) + " seconds");
    }

    /**
     * Translate CXE SubmissionStatusDto to internal BatchExecutionResult.
     */
    private BatchExecutionResult translateToResult(SubmissionStatusDto status) {
        // Determine execution status
        BatchExecutionResult.ExecutionStatus execStatus = determineStatus(status);

        // Convert testcase results
        List<TestCaseOutput> outputs = new ArrayList<>();
        if (status.getTestCaseResults() != null) {
            for (SubmissionStatusDto.TestCaseResult tcResult : status.getTestCaseResults()) {
                outputs.add(TestCaseOutput.builder()
                        .index(tcResult.getIndex())
                        .output(tcResult.getActualOutput())
                        .error(tcResult.getError())
                        .executionTimeMs(tcResult.getExecutionTimeMs())
                        .memoryKb(status.getMemoryKb() != null ? status.getMemoryKb().longValue() : null)
                        .build());
            }
        }

        return BatchExecutionResult.builder()
                .status(execStatus)
                .outputs(outputs)
                .compilationOutput(status.getCompilationOutput())
                .errorMessage(status.getErrorMessage())
                .totalRuntimeMs(status.getRuntimeMs() != null ? status.getRuntimeMs().longValue() : null)
                .peakMemoryKb(status.getMemoryKb() != null ? status.getMemoryKb().longValue() : null)
                .workerId(status.getWorkerId())
                .build();
    }

    /**
     * Determine internal execution status from CXE status.
     */
    private BatchExecutionResult.ExecutionStatus determineStatus(SubmissionStatusDto status) {
        // Check for compilation error
        if (status.getCompilationOutput() != null && !status.getCompilationOutput().isEmpty()) {
            String lower = status.getCompilationOutput().toLowerCase();
            if (lower.contains("error:") || lower.contains("cannot find symbol") ||
                    lower.contains("syntax error") || lower.contains("compilation failed")) {
                return BatchExecutionResult.ExecutionStatus.COMPILATION_ERROR;
            }
        }

        // Check for runtime error
        if (status.getErrorMessage() != null && !status.getErrorMessage().isEmpty()) {
            String lower = status.getErrorMessage().toLowerCase();
            if (lower.contains("timeout")) {
                return BatchExecutionResult.ExecutionStatus.TIMEOUT;
            }
            if (lower.contains("memory")) {
                return BatchExecutionResult.ExecutionStatus.MEMORY_LIMIT_EXCEEDED;
            }
            return BatchExecutionResult.ExecutionStatus.RUNTIME_ERROR;
        }

        // Check testcase results for errors
        if (status.getTestCaseResults() != null) {
            for (SubmissionStatusDto.TestCaseResult tc : status.getTestCaseResults()) {
                if (tc.getError() != null && !tc.getError().isEmpty()) {
                    return BatchExecutionResult.ExecutionStatus.RUNTIME_ERROR;
                }
            }
        }

        return BatchExecutionResult.ExecutionStatus.SUCCESS;
    }
}
