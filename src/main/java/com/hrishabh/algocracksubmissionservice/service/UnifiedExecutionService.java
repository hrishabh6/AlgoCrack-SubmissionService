package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.models.*;
import com.hrishabh.algocracksubmissionservice.adapter.ExecutionAdapter;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.dto.RunRequestDto;
import com.hrishabh.algocracksubmissionservice.dto.RunResponseDto;
import com.hrishabh.algocracksubmissionservice.dto.TestCaseDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.*;
import com.hrishabh.algocracksubmissionservice.exception.OracleMissingException;
import com.hrishabh.algocracksubmissionservice.judging.*;
import com.hrishabh.algocracksubmissionservice.logging.LoggingConstants;
import com.hrishabh.algocracksubmissionservice.logging.StructuredLogger;
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

        private final StructuredLogger structuredLogger = new StructuredLogger(UnifiedExecutionService.class,
                        "SubmissionService");

        private final ExecutionAdapter executionAdapter;
        private final OracleExecutionService oracleService;
        private final RunGuardService runGuard;
        private final ProblemServiceClient problemServiceClient;
        private final PipelineAssembler pipelineAssembler;

        /**
         * Execute code in RUN mode (synchronous).
         * 
         * @param request  Run request with code and testcases
         * @param clientIp Client IP for rate limiting
         * @return Run response with results
         */
        public RunResponseDto executeRun(RunRequestDto request, String clientIp) {
                String runId = "run-" + UUID.randomUUID().toString();
                structuredLogger.info("Run execution started",
                                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                LoggingConstants.OPERATION, "run_execute",
                                LoggingConstants.EXECUTION_ID, runId,
                                LoggingConstants.QUESTION_ID, request.getQuestionId(),
                                LoggingConstants.LANGUAGE, request.getLanguage(),
                                LoggingConstants.CLIENT_IP, clientIp,
                                LoggingConstants.CODE_LENGTH, request.getCode() != null ? request.getCode().length() : 0);

                try {
                        // 1. Determine testcases: custom or DEFAULT
                        List<TestCaseInput> testcases = resolveTestcases(request);

                        structuredLogger.debug("Run testcases resolved",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                        LoggingConstants.OPERATION, "resolve_testcases",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.QUESTION_ID, request.getQuestionId(),
                                        LoggingConstants.TESTCASE_COUNT, testcases.size(),
                                        "custom_testcases", testcases.stream().anyMatch(TestCaseInput::isCustom));

                        // 2. Apply rate limiting and validation
                        runGuard.validateRunRequest(testcases, clientIp);
                        structuredLogger.debug("Run guard validation passed",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                        LoggingConstants.OPERATION, "run_guard",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.CLIENT_IP, clientIp,
                                        LoggingConstants.TESTCASE_COUNT, testcases.size());

                        // 3. Validate oracle exists (fail fast before expensive compute)
                        if (!oracleService.hasOracle(request.getQuestionId())) {
                                throw new OracleMissingException(request.getQuestionId());
                        }
                        structuredLogger.debug("Oracle validation passed",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                        LoggingConstants.OPERATION, "oracle_validate",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.QUESTION_ID, request.getQuestionId());

                        // 4. Fetch question metadata via ProblemService API
                        QuestionMetadataApiDto metadata = problemServiceClient.getMetadata(
                                        request.getQuestionId(), request.getLanguage().toUpperCase());
                        if (metadata == null) {
                                throw new IllegalArgumentException(
                                                "Question metadata not found for language: " + request.getLanguage());
                        }

                        structuredLogger.debug("Question metadata fetched",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXTERNAL_CALL,
                                        LoggingConstants.OPERATION, "metadata_fetch",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.QUESTION_ID, request.getQuestionId(),
                                        LoggingConstants.LANGUAGE, request.getLanguage(),
                                        "function_name", metadata.getFunctionName(),
                                        "return_type", metadata.getReturnType());

                        // 5. Build code bundle for user execution
                        CodeBundle userBundle = buildCodeBundle(runId, request, testcases, metadata);

                        structuredLogger.debug("Run code bundle built",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                        LoggingConstants.OPERATION, "code_bundle_built",
                                        LoggingConstants.EXECUTION_ID, userBundle.getExecutionId(),
                                        LoggingConstants.QUESTION_ID, userBundle.getQuestionId(),
                                        LoggingConstants.LANGUAGE, userBundle.getLanguage(),
                                        LoggingConstants.CODE_LENGTH,
                                        userBundle.getCode() != null ? userBundle.getCode().length() : 0,
                                        LoggingConstants.TESTCASE_COUNT, userBundle.getTestcases().size(),
                                        "intent", userBundle.getIntent());

                        // 6. Execute user code
                        BatchExecutionResult userResult = executionAdapter.execute(userBundle);

                        structuredLogger.info("User code execution completed",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                        LoggingConstants.OPERATION, "user_code_execute",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.QUESTION_ID, request.getQuestionId(),
                                        LoggingConstants.STATUS, userResult.getStatus(),
                                        LoggingConstants.RUNTIME_MS, userResult.getTotalRuntimeMs(),
                                        LoggingConstants.MEMORY_KB, userResult.getPeakMemoryKb(),
                                        LoggingConstants.OUTPUT_COUNT,
                                        userResult.getOutputs() != null ? userResult.getOutputs().size() : 0,
                                        "has_error", userResult.getErrorMessage() != null,
                                        "has_compilation_output", userResult.getCompilationOutput() != null);

                        // Handle compilation/runtime errors
                        if (!userResult.isSuccess()) {
                                return handleExecutionError(userResult);
                        }

                        // 7. Execute oracle (batch - single CXE call)
                        BatchExecutionResult oracleResult = oracleService.executeOracle(
                                        request.getQuestionId(), testcases);

                        structuredLogger.info("Oracle execution completed for run",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                        LoggingConstants.OPERATION, "oracle_execute",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.QUESTION_ID, request.getQuestionId(),
                                        LoggingConstants.STATUS, oracleResult.getStatus(),
                                        LoggingConstants.OUTPUT_COUNT,
                                        oracleResult.getOutputs() != null ? oracleResult.getOutputs().size() : 0);

                        // 8. Compare results and build response
                        RunResponseDto response = buildRunResponse(userResult, oracleResult, metadata);

                        structuredLogger.info("Run execution completed",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                        LoggingConstants.OPERATION, "run_execute",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.QUESTION_ID, request.getQuestionId(),
                                        LoggingConstants.VERDICT, response.getVerdict(),
                                        LoggingConstants.STATUS, response.isSuccess() ? "SUCCESS" : "FAILED",
                                        LoggingConstants.RUNTIME_MS, response.getRuntimeMs(),
                                        LoggingConstants.MEMORY_KB, response.getMemoryKb());

                        return response;

                } catch (OracleMissingException e) {
                        structuredLogger.error("Run oracle missing",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                                        LoggingConstants.TYPE, "Error",
                                        LoggingConstants.OPERATION, "run_execute",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.QUESTION_ID, request.getQuestionId(),
                                        LoggingConstants.ERROR_MESSAGE, e.getMessage());
                        return RunResponseDto.error(RunVerdict.INTERNAL_ERROR_RUN,
                                        "Question not properly configured for testing");
                } catch (Exception e) {
                        structuredLogger.error("Run execution failed", e,
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                                        LoggingConstants.TYPE, "Error",
                                        LoggingConstants.OPERATION, "run_execute",
                                        LoggingConstants.EXECUTION_ID, runId,
                                        LoggingConstants.QUESTION_ID, request.getQuestionId());
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
                        // DEFAULT testcases via ProblemService API
                        List<TestCaseDto> apiTestcases = problemServiceClient.getTestCases(
                                        request.getQuestionId(), "DEFAULT");

                        return apiTestcases.stream()
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
                        List<TestCaseInput> testcases, QuestionMetadataApiDto metadata) {
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
                                .mutationTarget(metadata.getMutationTarget())
                                .serializationStrategy(metadata.getSerializationStrategy())
                                .questionType(metadata.getQuestionType())
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
         * Build response by comparing user output with oracle output via the judging
         * pipeline.
         */
        private RunResponseDto buildRunResponse(BatchExecutionResult userResult,
                        BatchExecutionResult oracleResult, QuestionMetadataApiDto metadata) {
                List<TestCaseOutput> userOutputs = userResult.getOutputs();
                List<TestCaseOutput> oracleOutputs = oracleResult.getOutputs();

                // Build judging context from metadata DTO
                JudgingContext judgingContext = buildJudgingContext(metadata);
                structuredLogger.debug("Run judging started",
                                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                LoggingConstants.OPERATION, "judge_run",
                                LoggingConstants.QUESTION_ID, metadata.getQuestionId(),
                                "user_output_count", userOutputs.size(),
                                "oracle_output_count", oracleOutputs.size(),
                                "return_type", judgingContext.getReturnType(),
                                "node_type", judgingContext.getNodeType(),
                                "order_matters", judgingContext.getIsOutputOrderMatters());

                // Assemble pipeline once per question (not per testcase)
                JudgingPipeline pipeline = pipelineAssembler.assemble(judgingContext);

                List<RunResponseDto.TestCaseRunResult> tcResults = new ArrayList<>();
                boolean allPassed = true;

                for (int i = 0; i < userOutputs.size(); i++) {
                        TestCaseOutput userOutput = userOutputs.get(i);
                        TestCaseOutput oracleOutput = (i < oracleOutputs.size()) ? oracleOutputs.get(i) : null;

                        ExecutionOutput userExecOutput = ExecutionOutput.builder()
                                        .rawOutput(userOutput.getOutput())
                                        .error(userOutput.getError())
                                        .executionTimeMs(userOutput.getExecutionTimeMs())
                                        .build();

                        ExecutionOutput oracleExecOutput = ExecutionOutput.builder()
                                        .rawOutput(oracleOutput != null ? oracleOutput.getOutput() : null)
                                        .error(oracleOutput != null ? oracleOutput.getError() : null)
                                        .build();

                        JudgingResult result = pipeline.judge(userExecOutput, oracleExecOutput, judgingContext);

                        boolean passed = result.isPassed();
                        if (!passed) {
                                allPassed = false;
                        }

                        structuredLogger.debug("Run testcase judged",
                                        LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                        LoggingConstants.OPERATION, "judge_testcase",
                                        LoggingConstants.QUESTION_ID, metadata.getQuestionId(),
                                        "testcase_index", i,
                                        "passed", passed,
                                        "has_user_error", userOutput.getError() != null,
                                        "has_oracle_output", oracleOutput != null,
                                        "failure_reason", result.getFailureReason());

                        tcResults.add(RunResponseDto.TestCaseRunResult.builder()
                                        .index(i)
                                        .passed(passed)
                                        .actualOutput(userOutput.getOutput())
                                        .expectedOutput(oracleOutput != null ? oracleOutput.getOutput() : null)
                                        .executionTimeMs(userOutput.getExecutionTimeMs())
                                        .error(userOutput.getError())
                                        .build());
                }

                RunVerdict verdict = allPassed ? RunVerdict.PASSED_RUN : RunVerdict.FAILED_RUN;

                structuredLogger.info("Run judging completed",
                                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                                LoggingConstants.OPERATION, "judge_run",
                                LoggingConstants.QUESTION_ID, metadata.getQuestionId(),
                                LoggingConstants.VERDICT, verdict,
                                LoggingConstants.STATUS, allPassed ? "SUCCESS" : "FAILED",
                                LoggingConstants.TESTCASE_COUNT, tcResults.size());

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

        /**
         * Build JudgingContext from question metadata API DTO.
         * Reads fields directly from DTO instead of navigating JPA relationships.
         */
        private JudgingContext buildJudgingContext(QuestionMetadataApiDto metadata) {
                // Parse comma-separated validationHints
                java.util.List<String> hints = null;
                if (metadata.getValidationHints() != null && !metadata.getValidationHints().isBlank()) {
                        hints = java.util.Arrays.stream(metadata.getValidationHints().split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .toList();
                }

                // Parse nodeType from String to enum
                NodeType nodeType = null;
                if (metadata.getNodeType() != null && !metadata.getNodeType().isBlank()) {
                        try {
                                nodeType = NodeType.valueOf(metadata.getNodeType());
                        } catch (IllegalArgumentException ignored) {
                        }
                }

                // Resolve effective output type for normalizer/comparator routing.
                String effectiveOutputType = resolveEffectiveOutputType(metadata);

                return JudgingContext.builder()
                                .returnType(metadata.getReturnType())
                                .executionStrategy(metadata.getExecutionStrategy())
                                .questionId(metadata.getQuestionId())
                                .nodeType(nodeType)
                                .isOutputOrderMatters(metadata.getIsOutputOrderMatters())
                                .validationHints(hints)
                                .mutationTarget(metadata.getMutationTarget())
                                .serializationStrategy(metadata.getSerializationStrategy())
                                .questionType(metadata.getQuestionType())
                                .effectiveOutputType(effectiveOutputType)
                                .build();
        }

        /**
         * Resolve the effective output type for normalizer/comparator routing.
         */
        private String resolveEffectiveOutputType(QuestionMetadataApiDto metadata) {
                String returnType = metadata.getReturnType();
                if (returnType != null && !"void".equalsIgnoreCase(returnType)) {
                        return returnType;
                }

                // Void return — resolve from mutation target's param type.
                java.util.List<String> paramTypes = metadata.getParamTypes();
                String target = metadata.getMutationTarget();

                if (target != null && !target.isBlank() && paramTypes != null) {
                        try {
                                int idx = Integer.parseInt(target.trim());
                                if (idx >= 0 && idx < paramTypes.size()) {
                                        return paramTypes.get(idx);
                                }
                        } catch (NumberFormatException ignored) {
                        }
                }

                if (paramTypes != null && paramTypes.size() == 1) {
                        return paramTypes.get(0);
                }

                return returnType;
        }
}
