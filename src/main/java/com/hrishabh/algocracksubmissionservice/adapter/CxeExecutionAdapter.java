package com.hrishabh.algocracksubmissionservice.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocrackentityservice.models.Language;
import com.hrishabh.algocrackentityservice.models.QuestionMetadata;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionRequest;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionResponse;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.*;
import com.hrishabh.algocracksubmissionservice.repository.QuestionMetadataRepository;
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

    private final CodeExecutionClientService cxeClient;
    private final QuestionMetadataRepository metadataRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_POLL_ATTEMPTS = 60;
    private static final int POLL_INTERVAL_MS = 500;

    @Override
    public BatchExecutionResult execute(CodeBundle codeBundle) {
        log.info("[{}] Executing via CXE adapter", codeBundle.getExecutionId());

        System.out.println("\n" + "*".repeat(80));
        System.out.println("[CxeExecutionAdapter] execute() CALLED");
        System.out.println("*".repeat(80));
        System.out.println("[CxeExecutionAdapter] Input CodeBundle:");
        System.out.println("    executionId: " + codeBundle.getExecutionId());
        System.out.println("    questionId: " + codeBundle.getQuestionId());
        System.out.println("    language: " + codeBundle.getLanguage());
        System.out.println("    userId: " + codeBundle.getUserId());
        System.out.println("    intent: " + codeBundle.getIntent());
        System.out.println("    code length: " + (codeBundle.getCode() != null ? codeBundle.getCode().length() : 0));
        System.out.println(
                "    testcases count: " + (codeBundle.getTestcases() != null ? codeBundle.getTestcases().size() : 0));

        try {
            // 1. Fetch question metadata if not provided
            CodeBundle.QuestionMetadataBundle metadataBundle = codeBundle.getMetadata();
            if (metadataBundle == null) {
                metadataBundle = fetchMetadata(codeBundle.getQuestionId(), codeBundle.getLanguage());
                codeBundle.setMetadata(metadataBundle);
            }

            // 2. Translate internal DTO → CXE DTO
            ExecutionRequest cxeRequest = translateToRequest(codeBundle);

            System.out.println("\n[CxeExecutionAdapter] CXE ExecutionRequest DTO (SENDING TO CXE):");
            System.out.println("    submissionId: " + cxeRequest.getSubmissionId());
            System.out.println("    userId: " + cxeRequest.getUserId());
            System.out.println("    questionId: " + cxeRequest.getQuestionId());
            System.out.println("    language: " + cxeRequest.getLanguage());
            System.out
                    .println("    code length: " + (cxeRequest.getCode() != null ? cxeRequest.getCode().length() : 0));
            System.out.println("    metadata.functionName: "
                    + (cxeRequest.getMetadata() != null ? cxeRequest.getMetadata().getFunctionName() : "null"));
            System.out.println("    metadata.returnType: "
                    + (cxeRequest.getMetadata() != null ? cxeRequest.getMetadata().getReturnType() : "null"));
            System.out.println("    metadata.parameters: "
                    + (cxeRequest.getMetadata() != null ? cxeRequest.getMetadata().getParameters() : "null"));
            System.out.println("    testCases count: "
                    + (cxeRequest.getTestCases() != null ? cxeRequest.getTestCases().size() : 0));

            if (cxeRequest.getTestCases() != null) {
                for (int i = 0; i < cxeRequest.getTestCases().size(); i++) {
                    System.out.println("    testCase[" + i + "]: " + cxeRequest.getTestCases().get(i));
                }
            }

            // 3. Submit to CXE
            System.out.println("\n[CxeExecutionAdapter] Submitting to CXE...");
            log.debug("[{}] Submitting to CXE", codeBundle.getExecutionId());
            ExecutionResponse response = cxeClient.submitCode(cxeRequest);

            System.out.println("[CxeExecutionAdapter] CXE Submit Response:");
            System.out.println("    submissionId: " + response.getSubmissionId());
            System.out.println("    status: " + response.getStatus());
            System.out.println("    message: " + response.getMessage());
            System.out.println("    queuePosition: " + response.getQueuePosition());

            // 4. Poll for completion
            System.out.println("\n[CxeExecutionAdapter] Polling CXE for completion...");
            SubmissionStatusDto status = pollForCompletion(response.getSubmissionId());

            System.out.println("\n[CxeExecutionAdapter] CXE Final Status (RECEIVED FROM CXE):");
            System.out.println("    submissionId: " + status.getSubmissionId());
            System.out.println("    status: " + status.getStatus());
            System.out.println("    verdict: " + status.getVerdict());
            System.out.println("    runtimeMs: " + status.getRuntimeMs());
            System.out.println("    memoryKb: " + status.getMemoryKb());
            System.out.println("    errorMessage: " + status.getErrorMessage());
            System.out.println("    compilationOutput: " + status.getCompilationOutput());
            System.out.println("    workerId: " + status.getWorkerId());
            if (status.getTestCaseResults() != null) {
                System.out.println("    testCaseResults count: " + status.getTestCaseResults().size());
                for (int i = 0; i < status.getTestCaseResults().size(); i++) {
                    SubmissionStatusDto.TestCaseResult tc = status.getTestCaseResults().get(i);
                    System.out.println("    testCaseResult[" + i + "]:");
                    System.out.println("        index: " + tc.getIndex());
                    System.out.println("        passed: " + tc.getPassed());
                    System.out.println("        actualOutput: " + tc.getActualOutput());
                    System.out.println("        expectedOutput: " + tc.getExpectedOutput());
                    System.out.println("        executionTimeMs: " + tc.getExecutionTimeMs());
                    System.out.println("        error: " + tc.getError());
                }
            }

            // 5. Translate CXE DTO → internal DTO
            BatchExecutionResult result = translateToResult(status);

            System.out.println("\n[CxeExecutionAdapter] Translated BatchExecutionResult:");
            System.out.println("    status: " + result.getStatus());
            System.out.println("    isSuccess: " + result.isSuccess());
            System.out.println("    outputs count: " + (result.getOutputs() != null ? result.getOutputs().size() : 0));
            System.out.println("*".repeat(80) + "\n");

            return result;

        } catch (Exception e) {
            log.error("[{}] CXE execution failed: {}", codeBundle.getExecutionId(), e.getMessage(), e);
            System.out.println(
                    "[CxeExecutionAdapter] EXCEPTION: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return BatchExecutionResult.builder()
                    .status(BatchExecutionResult.ExecutionStatus.INTERNAL_ERROR)
                    .errorMessage(e.getMessage())
                    .outputs(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Fetch question metadata from DB.
     */
    private CodeBundle.QuestionMetadataBundle fetchMetadata(Long questionId, Language language) {
        QuestionMetadata metadata = metadataRepository
                .findByQuestionIdAndLanguage(questionId, language)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Question metadata not found for questionId: " + questionId + ", language: " + language));

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
