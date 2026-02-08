package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocrackentityservice.models.*;
import com.hrishabh.algocracksubmissionservice.adapter.ExecutionAdapter;
import com.hrishabh.algocracksubmissionservice.dto.RunRequestDto;
import com.hrishabh.algocracksubmissionservice.dto.RunResponseDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.*;
import com.hrishabh.algocracksubmissionservice.exception.OracleMissingException;
import com.hrishabh.algocracksubmissionservice.repository.QuestionMetadataRepository;
import com.hrishabh.algocracksubmissionservice.repository.TestcaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Unified execution service for both RUN and SUBMIT modes.
 * 
 * - RUN: Synchronous, user-visible testcases, returns raw outputs (no
 * persistence)
 * - SUBMIT: Async, hidden testcases, persists results, authoritative verdict
 * 
 * This service handles RUN mode. SUBMIT continues to use
 * SubmissionProcessingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedExecutionService {

    private final ExecutionAdapter executionAdapter;
    private final OracleExecutionService oracleService;
    private final ResultValidationService validationService;
    private final RunGuardService runGuard;
    private final QuestionMetadataRepository metadataRepository;
    private final TestcaseRepository testcaseRepository;

    /**
     * Execute code in RUN mode (synchronous).
     * 
     * @param request  Run request with code and testcases
     * @param clientIp Client IP for rate limiting
     * @return Run response with results
     */
    public RunResponseDto executeRun(RunRequestDto request, String clientIp) {
        String runId = "run-" + UUID.randomUUID().toString();
        log.info("[{}] Starting RUN for question {}", runId, request.getQuestionId());

        System.out.println("\n" + "-".repeat(80));
        System.out.println("[UnifiedExecutionService] executeRun() STARTED - runId: " + runId);
        System.out.println("-".repeat(80));

        try {
            // 1. Determine testcases: custom or DEFAULT
            List<TestCaseInput> testcases = resolveTestcases(request);

            System.out.println("[UnifiedExecutionService] Step 1: Resolved TestCases");
            System.out.println("[UnifiedExecutionService] TestCase Count: " + testcases.size());
            for (int i = 0; i < testcases.size(); i++) {
                System.out.println("[UnifiedExecutionService] TestCase[" + i + "]:");
                System.out.println("    input: " + testcases.get(i).getInput());
                System.out.println("    isCustom: " + testcases.get(i).isCustom());
            }

            // 2. Apply rate limiting and validation
            runGuard.validateRunRequest(testcases, clientIp);
            System.out.println("[UnifiedExecutionService] Step 2: Rate limit validation PASSED");

            // 3. Validate oracle exists (fail fast before expensive compute)
            if (!oracleService.hasOracle(request.getQuestionId())) {
                throw new OracleMissingException(request.getQuestionId());
            }
            System.out.println("[UnifiedExecutionService] Step 3: Oracle validation PASSED");

            // 4. Fetch question metadata
            Language language = Language.valueOf(request.getLanguage().toUpperCase());
            QuestionMetadata metadata = metadataRepository
                    .findByQuestionIdAndLanguage(request.getQuestionId(), language)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Question metadata not found for language: " + language));

            System.out.println("[UnifiedExecutionService] Step 4: Question Metadata");
            System.out.println("    functionName: " + metadata.getFunctionName());
            System.out.println("    returnType: " + metadata.getReturnType());
            System.out.println("    paramNames: " + metadata.getParamNames());
            System.out.println("    paramTypes: " + metadata.getParamTypes());

            // 5. Build code bundle for user execution
            CodeBundle userBundle = buildCodeBundle(runId, request, testcases, metadata);

            System.out.println("[UnifiedExecutionService] Step 5: User CodeBundle Built");
            System.out.println("    executionId: " + userBundle.getExecutionId());
            System.out.println("    language: " + userBundle.getLanguage());
            System.out.println("    questionId: " + userBundle.getQuestionId());
            System.out
                    .println("    code length: " + (userBundle.getCode() != null ? userBundle.getCode().length() : 0));
            System.out.println("    testcases count: " + userBundle.getTestcases().size());
            System.out.println("    intent: " + userBundle.getIntent());
            System.out.println("    metadata.functionName: " + userBundle.getMetadata().getFunctionName());

            // 6. Execute user code
            System.out.println("\n[UnifiedExecutionService] Step 6: EXECUTING USER CODE via ExecutionAdapter...");
            log.debug("[{}] Executing user code", runId);
            BatchExecutionResult userResult = executionAdapter.execute(userBundle);

            System.out.println("[UnifiedExecutionService] User Execution RESULT:");
            System.out.println("    status: " + userResult.getStatus());
            System.out.println("    isSuccess: " + userResult.isSuccess());
            System.out.println("    compilationOutput: " + userResult.getCompilationOutput());
            System.out.println("    errorMessage: " + userResult.getErrorMessage());
            System.out.println("    totalRuntimeMs: " + userResult.getTotalRuntimeMs());
            System.out.println(
                    "    outputs count: " + (userResult.getOutputs() != null ? userResult.getOutputs().size() : 0));
            if (userResult.getOutputs() != null) {
                for (int i = 0; i < userResult.getOutputs().size(); i++) {
                    TestCaseOutput o = userResult.getOutputs().get(i);
                    System.out.println("    output[" + i + "]: " + o.getOutput() + " (error=" + o.getError() + ")");
                }
            }

            // Handle compilation/runtime errors
            if (!userResult.isSuccess()) {
                System.out.println("[UnifiedExecutionService] User execution FAILED - returning error response");
                return handleExecutionError(userResult);
            }

            // 7. Execute oracle (batch - single CXE call)
            System.out.println("\n[UnifiedExecutionService] Step 7: EXECUTING ORACLE...");
            log.debug("[{}] Executing oracle", runId);
            BatchExecutionResult oracleResult = oracleService.executeOracle(
                    request.getQuestionId(), testcases);

            System.out.println("[UnifiedExecutionService] Oracle Execution RESULT:");
            System.out.println("    status: " + oracleResult.getStatus());
            System.out.println("    isSuccess: " + oracleResult.isSuccess());
            System.out.println(
                    "    outputs count: " + (oracleResult.getOutputs() != null ? oracleResult.getOutputs().size() : 0));
            if (oracleResult.getOutputs() != null) {
                for (int i = 0; i < oracleResult.getOutputs().size(); i++) {
                    TestCaseOutput o = oracleResult.getOutputs().get(i);
                    System.out.println("    output[" + i + "]: " + o.getOutput());
                }
            }

            // 8. Compare results and build response
            System.out.println("\n[UnifiedExecutionService] Step 8: COMPARING RESULTS (Judging)...");
            RunResponseDto response = buildRunResponse(userResult, oracleResult);

            System.out.println("[UnifiedExecutionService] Final Response Built:");
            System.out.println("    verdict: " + response.getVerdict());
            System.out.println("    success: " + response.isSuccess());
            System.out.println("-".repeat(80) + "\n");

            return response;

        } catch (OracleMissingException e) {
            log.error("[{}] Oracle missing: {}", runId, e.getMessage());
            System.out.println("[UnifiedExecutionService] ERROR: Oracle missing - " + e.getMessage());
            return RunResponseDto.error(RunVerdict.INTERNAL_ERROR_RUN,
                    "Question not properly configured for testing");
        } catch (Exception e) {
            log.error("[{}] RUN failed: {}", runId, e.getMessage(), e);
            System.out.println(
                    "[UnifiedExecutionService] ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return RunResponseDto.error(RunVerdict.INTERNAL_ERROR_RUN, e.getMessage());
        }
    }

    /**
     * Resolve testcases: use custom if provided, otherwise DEFAULT from DB.
     */
    private List<TestCaseInput> resolveTestcases(RunRequestDto request) {
        if (request.getCustomTestCases() != null && !request.getCustomTestCases().isEmpty()) {
            // Custom testcases from user
            return request.getCustomTestCases().stream()
                    .map(tc -> TestCaseInput.builder()
                            .input(tc.getInput())
                            .isCustom(true)
                            .build())
                    .collect(Collectors.toList());
        } else {
            // DEFAULT testcases from DB
            List<TestCase> dbTestcases = testcaseRepository.findByQuestionIdAndType(
                    request.getQuestionId(), TestCaseType.DEFAULT);

            return dbTestcases.stream()
                    .map(tc -> TestCaseInput.builder()
                            .input(tc.getInput())
                            .isCustom(false)
                            .build())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Build code bundle for execution.
     */
    private CodeBundle buildCodeBundle(String runId, RunRequestDto request,
            List<TestCaseInput> testcases, QuestionMetadata metadata) {
        // Convert metadata
        List<CodeBundle.Parameter> params = new ArrayList<>();
        List<String> paramNames = metadata.getParamNames();
        List<String> paramTypes = metadata.getParamTypes();
        for (int i = 0; i < paramNames.size() && i < paramTypes.size(); i++) {
            params.add(CodeBundle.Parameter.builder()
                    .name(paramNames.get(i))
                    .type(paramTypes.get(i))
                    .build());
        }

        CodeBundle.QuestionMetadataBundle metaBundle = CodeBundle.QuestionMetadataBundle.builder()
                .fullyQualifiedPackageName("com.algocrack.solution.q" + request.getQuestionId())
                .functionName(metadata.getFunctionName())
                .returnType(metadata.getReturnType())
                .parameters(params)
                .customDataStructureNames(new ArrayList<>())
                .build();

        return CodeBundle.builder()
                .executionId(runId)
                .code(request.getCode())
                .language(Language.valueOf(request.getLanguage().toUpperCase()))
                .questionId(request.getQuestionId())
                .testcases(testcases)
                .metadata(metaBundle)
                .intent(ExecutionIntent.RUN)
                .build();
    }

    /**
     * Handle execution errors (compilation, runtime, etc.)
     */
    private RunResponseDto handleExecutionError(BatchExecutionResult result) {
        RunVerdict verdict;
        switch (result.getStatus()) {
            case COMPILATION_ERROR:
                verdict = RunVerdict.COMPILATION_ERROR_RUN;
                break;
            case TIMEOUT:
                verdict = RunVerdict.TIMEOUT_RUN;
                break;
            case MEMORY_LIMIT_EXCEEDED:
                verdict = RunVerdict.MEMORY_LIMIT_RUN;
                break;
            case RUNTIME_ERROR:
                verdict = RunVerdict.RUNTIME_ERROR_RUN;
                break;
            default:
                verdict = RunVerdict.INTERNAL_ERROR_RUN;
        }

        return RunResponseDto.builder()
                .verdict(verdict)
                .success(false)
                .errorMessage(result.getErrorMessage())
                .compilationOutput(result.getCompilationOutput())
                .build();
    }

    /**
     * Build response by comparing user output with oracle output.
     */
    private RunResponseDto buildRunResponse(BatchExecutionResult userResult,
            BatchExecutionResult oracleResult) {
        List<TestCaseOutput> userOutputs = userResult.getOutputs();
        List<TestCaseOutput> oracleOutputs = oracleResult.getOutputs();

        System.out.println("\n" + "~".repeat(60));
        System.out.println("[buildRunResponse] JUDGING - Comparing User vs Oracle Outputs");
        System.out.println("~".repeat(60));
        System.out.println("[buildRunResponse] User outputs count: " + userOutputs.size());
        System.out.println("[buildRunResponse] Oracle outputs count: " + oracleOutputs.size());

        List<RunResponseDto.TestCaseRunResult> tcResults = new ArrayList<>();
        boolean allPassed = true;

        for (int i = 0; i < userOutputs.size(); i++) {
            TestCaseOutput userOutput = userOutputs.get(i);
            String expectedOutput = (i < oracleOutputs.size()) ? oracleOutputs.get(i).getOutput() : null;

            System.out.println("\n[buildRunResponse] TestCase[" + i + "] COMPARISON:");
            System.out.println("    userOutput:     \"" + userOutput.getOutput() + "\"");
            System.out.println("    expectedOutput: \"" + expectedOutput + "\"");
            
            boolean passed = validationService.outputsMatch(userOutput.getOutput(), expectedOutput);
            System.out.println("    outputsMatch(): " + passed);
            
            if (!passed) {
                allPassed = false;
            }

            tcResults.add(RunResponseDto.TestCaseRunResult.builder()
                    .index(i)
                    .passed(passed)
                    .actualOutput(userOutput.getOutput())
                    .expectedOutput(expectedOutput)
                    .executionTimeMs(userOutput.getExecutionTimeMs())
                    .error(userOutput.getError())
                    .build());
        }

        RunVerdict verdict = allPassed ? RunVerdict.PASSED_RUN : RunVerdict.FAILED_RUN;
        
        System.out.println("\n[buildRunResponse] FINAL VERDICT: " + verdict);
        System.out.println("[buildRunResponse] allPassed: " + allPassed);
        System.out.println("~".repeat(60) + "\n");

        return RunResponseDto.builder()
                .verdict(verdict)
                .success(allPassed)
                .runtimeMs(userResult.getTotalRuntimeMs() != null
                        ? userResult.getTotalRuntimeMs().intValue()
                        : null)
                .memoryKb(userResult.getPeakMemoryKb() != null
                        ? userResult.getPeakMemoryKb().intValue()
                        : null)
                .testCaseResults(tcResults)
                .build();
    }
}
