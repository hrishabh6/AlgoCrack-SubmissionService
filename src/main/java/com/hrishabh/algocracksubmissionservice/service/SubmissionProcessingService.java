package com.hrishabh.algocracksubmissionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.models.*;
import com.hrishabh.algocracksubmissionservice.adapter.ExecutionAdapter;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.dto.TestCaseDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.*;
import com.hrishabh.algocracksubmissionservice.judging.*;
import com.hrishabh.algocracksubmissionservice.repository.QuestionStatisticsRepository;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Async processing service for code submissions.
 *
 * Both user submissions and oracle executions go through the same
 * ExecutionAdapter pipeline, and judging uses the same JudgingPipeline
 * as the RUN path — no divergent judging systems.
 *
 * Error handling (compilation, runtime, timeout) stays in this orchestration
 * layer. Only semantic correctness flows through the JudgingPipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionProcessingService {

    private final ExecutionAdapter executionAdapter;
    private final PipelineAssembler pipelineAssembler;
    private final SubmissionRepository submissionRepository;
    private final QuestionStatisticsRepository statsRepository;
    private final ProblemServiceClient problemServiceClient;
    private final WebSocketService webSocketService;
    private final OracleExecutionService oracleExecutionService;
    private final ObjectMapper objectMapper;

    /**
     * Process submission asynchronously.
     *
     * @param submissionId The UUID of the submission to process (NOT the entity to
     *                     avoid detached entity issues)
     */
    @Async
    @Transactional
    public void processSubmission(String submissionId) {
        log.info("Starting async processing for submission: {}", submissionId);

        // Re-fetch submission within this transaction to avoid detached entity issues
        Submission submission = submissionRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found: " + submissionId));

        try {
            // 1. Update status to COMPILING
            submission.setStatus(SubmissionStatus.COMPILING);
            submission.setStartedAt(LocalDateTime.now());
            submission = submissionRepository.save(submission);
            webSocketService.sendStatus(submission);

            // 2. Fetch question metadata and test cases via ProblemService API
            Long questionId = submission.getQuestionId();
            QuestionMetadataApiDto metadata = problemServiceClient.getMetadata(
                    questionId, submission.getLanguage().toUpperCase());
            if (metadata == null) {
                throw new RuntimeException(
                        "Question metadata not found for questionId: " + questionId + ", language: " + submission.getLanguage());
            }

            // Fetch HIDDEN testcases via ProblemService API
            List<TestCaseDto> testCases = problemServiceClient.getTestCases(questionId, "HIDDEN");

            // 3. Build CodeBundle and execute via adapter (same pipeline as RUN and ORACLE)
            List<TestCaseInput> testCaseInputs = testCases.stream()
                    .map(tc -> TestCaseInput.builder().input(tc.getInput()).build())
                    .collect(Collectors.toList());

            CodeBundle userBundle = buildCodeBundle(submission, metadata, testCaseInputs);

            // 4. Update status to RUNNING
            submission.setStatus(SubmissionStatus.RUNNING);
            submission = submissionRepository.save(submission);
            webSocketService.sendStatus(submission);

            // 5. Execute user code via adapter
            log.info("Executing user code for: {}", submissionId);
            BatchExecutionResult userResult = executionAdapter.execute(userBundle);

            // 6. Handle execution-layer errors BEFORE pipeline
            // (compilation, runtime, timeout — these are not semantic correctness issues)
            if (!userResult.isSuccess()) {
                SubmissionVerdict errorVerdict = mapExecutionErrorToVerdict(userResult);
                log.info("Execution error for {}: {}", submissionId, errorVerdict);
                finalizeSubmission(submission, errorVerdict, userResult, null);
                return;
            }

            // 7. Execute oracle to get expected outputs
            log.info("Executing oracle for question: {}", questionId);
            List<TestCaseInput> oracleInputs = testCases.stream()
                    .map(tc -> TestCaseInput.builder().input(tc.getInput()).build())
                    .collect(Collectors.toList());
            BatchExecutionResult oracleResult = oracleExecutionService.executeOracle(questionId, oracleInputs);

            if (!oracleResult.isSuccess()) {
                log.error("Oracle execution failed for question {}: {}", questionId, oracleResult.getStatus());
                finalizeSubmission(submission, SubmissionVerdict.INTERNAL_ERROR, userResult, null);
                return;
            }

            // 8. Judge via JudgingPipeline (same pipeline as RUN path)
            SubmissionVerdict verdict = judgeViaPipeline(userResult, oracleResult, metadata);

            // 9. Finalize submission
            finalizeSubmission(submission, verdict, userResult,
                    buildTestResultsJson(userResult.getOutputs()));

        } catch (Exception e) {
            log.error("Processing failed for {}: {}", submissionId, e.getMessage(), e);

            submission.setStatus(SubmissionStatus.FAILED);
            submission.setErrorMessage(e.getMessage());
            submission.setCompletedAt(LocalDateTime.now());
            submissionRepository.save(submission);

            webSocketService.sendError(submission, e.getMessage());
        }
    }

    /**
     * Judge user results against oracle results via the JudgingPipeline.
     * Same pipeline as the RUN path — no divergent judging systems.
     */
    private SubmissionVerdict judgeViaPipeline(BatchExecutionResult userResult,
            BatchExecutionResult oracleResult, QuestionMetadataApiDto metadata) {
        List<TestCaseOutput> userOutputs = userResult.getOutputs();
        List<TestCaseOutput> oracleOutputs = oracleResult.getOutputs();

        // Build judging context (same as UnifiedExecutionService)
        JudgingContext judgingContext = buildJudgingContext(metadata);

        // Assemble pipeline ONCE per question
        JudgingPipeline pipeline = pipelineAssembler.assemble(judgingContext);

        log.debug("Judging {} testcases via pipeline", userOutputs.size());

        for (int i = 0; i < userOutputs.size(); i++) {
            TestCaseOutput userOutput = userOutputs.get(i);
            TestCaseOutput oracleOutput = (i < oracleOutputs.size()) ? oracleOutputs.get(i) : null;

            // Check for per-testcase runtime error (user code ran but errored on this case)
            if (userOutput.getError() != null && !userOutput.getError().isEmpty()) {
                log.info("Runtime error on test case {}: {}", i, userOutput.getError());
                return SubmissionVerdict.RUNTIME_ERROR;
            }

            // Build ExecutionOutput wrappers for the pipeline
            ExecutionOutput userExecOutput = ExecutionOutput.builder()
                    .rawOutput(userOutput.getOutput())
                    .error(userOutput.getError())
                    .executionTimeMs(userOutput.getExecutionTimeMs())
                    .build();

            ExecutionOutput oracleExecOutput = ExecutionOutput.builder()
                    .rawOutput(oracleOutput != null ? oracleOutput.getOutput() : null)
                    .error(oracleOutput != null ? oracleOutput.getError() : null)
                    .build();

            // Judge via pipeline
            JudgingResult result = pipeline.judge(userExecOutput, oracleExecOutput, judgingContext);

            if (result.isJudgeError()) {
                log.error("Judge error on test case {}: {}", i, result.getFailureReason());
                return SubmissionVerdict.INTERNAL_ERROR;
            }

            if (!result.isPassed()) {
                log.info("Wrong answer on test case {}: {}", i, result.getFailureReason());
                return SubmissionVerdict.WRONG_ANSWER;
            }
        }

        log.info("All test cases passed");
        return SubmissionVerdict.ACCEPTED;
    }

    /**
     * Map execution-level errors to submission verdicts.
     * These are handled BEFORE the pipeline — they are not semantic correctness
     * issues.
     */
    private SubmissionVerdict mapExecutionErrorToVerdict(BatchExecutionResult result) {
        if (result.isCompilationError()) {
            return SubmissionVerdict.COMPILATION_ERROR;
        }
        switch (result.getStatus()) {
            case RUNTIME_ERROR:
                return SubmissionVerdict.RUNTIME_ERROR;
            case TIMEOUT:
                return SubmissionVerdict.TIME_LIMIT_EXCEEDED;
            case MEMORY_LIMIT_EXCEEDED:
                return SubmissionVerdict.MEMORY_LIMIT_EXCEEDED;
            default:
                return SubmissionVerdict.INTERNAL_ERROR;
        }
    }

    /**
     * Finalize submission with results and notify client.
     */
    private void finalizeSubmission(Submission submission, SubmissionVerdict verdict,
            BatchExecutionResult userResult, String testResults) {
        submission.setStatus(SubmissionStatus.COMPLETED);
        submission.setVerdict(verdict);
        submission.setRuntimeMs(userResult.getTotalRuntimeMs() != null
                ? userResult.getTotalRuntimeMs().intValue()
                : null);
        submission.setMemoryKb(userResult.getPeakMemoryKb() != null
                ? userResult.getPeakMemoryKb().intValue()
                : null);
        submission.setCompletedAt(LocalDateTime.now());
        submission.setWorkerId(userResult.getWorkerId());
        submission.setTestResults(testResults != null ? testResults
                : buildTestResultsJson(userResult.getOutputs()));
        submission.setCompilationOutput(userResult.getCompilationOutput());
        submission.setErrorMessage(userResult.getErrorMessage());
        submissionRepository.save(submission);

        log.info("Submission {} completed with verdict: {}", submission.getSubmissionId(), verdict);

        // Update question statistics
        updateQuestionStatistics(submission);

        // Notify client
        webSocketService.sendResult(submission);
    }

    /**
     * Build JudgingContext from question metadata.
     * Identical logic to UnifiedExecutionService.buildJudgingContext().
     */
    private JudgingContext buildJudgingContext(QuestionMetadataApiDto metadata) {
        // Parse comma-separated validationHints
        List<String> hints = null;
        if (metadata.getValidationHints() != null && !metadata.getValidationHints().isBlank()) {
            hints = java.util.Arrays.stream(metadata.getValidationHints().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        // Parse nodeType from String to enum
        NodeType nodeType = null;
        if (metadata.getNodeType() != null && !metadata.getNodeType().isBlank()) {
            try { nodeType = NodeType.valueOf(metadata.getNodeType()); } catch (IllegalArgumentException ignored) {}
        }

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
     * For void-return functions with a mutation target, the actual serialized
     * output is the mutation target's parameter type (e.g., char[][] for Sudoku).
     */
    private String resolveEffectiveOutputType(QuestionMetadataApiDto metadata) {
        String returnType = metadata.getReturnType();
        if (returnType != null && !"void".equalsIgnoreCase(returnType)) {
            return returnType;
        }
        List<String> paramTypes = metadata.getParamTypes();
        String target = metadata.getMutationTarget();
        if (target != null && !target.isBlank() && paramTypes != null) {
            try {
                int idx = Integer.parseInt(target.trim());
                if (idx >= 0 && idx < paramTypes.size()) { return paramTypes.get(idx); }
            } catch (NumberFormatException ignored) {}
        }
        if (paramTypes != null && paramTypes.size() == 1) { return paramTypes.get(0); }
        return returnType;
    }

    /**
     * Build CodeBundle for user submission execution.
     */
    private CodeBundle buildCodeBundle(Submission submission, QuestionMetadataApiDto metadata,
            List<TestCaseInput> testcases) {
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
                .fullyQualifiedPackageName("com.algocrack.solution.q" + submission.getQuestionId())
                .functionName(metadata.getFunctionName())
                .returnType(metadata.getReturnType())
                .parameters(params)
                .customDataStructureNames(new ArrayList<>())
                .mutationTarget(metadata.getMutationTarget())
                .serializationStrategy(metadata.getSerializationStrategy())
                .questionType(metadata.getQuestionType())
                .build();

        return CodeBundle.builder()
                .executionId(submission.getSubmissionId())
                .code(submission.getCode())
                .language(Language.valueOf(submission.getLanguage().toUpperCase()))
                .questionId(submission.getQuestionId())
                .userId(submission.getUserId())
                .testcases(testcases)
                .metadata(metaBundle)
                .intent(ExecutionIntent.SUBMIT)
                .build();
    }

    /**
     * Build testResults JSON from execution outputs.
     */
    private String buildTestResultsJson(List<TestCaseOutput> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return "[]";
        }
        try {
            List<Map<String, Object>> testResults = new ArrayList<>();
            for (TestCaseOutput output : outputs) {
                Map<String, Object> result = new HashMap<>();
                result.put("index", output.getIndex());
                result.put("passed", output.getError() == null || output.getError().isEmpty());
                result.put("time", output.getExecutionTimeMs());
                result.put("output", output.getOutput());
                if (output.getError() != null) {
                    result.put("error", output.getError());
                }
                testResults.add(result);
            }
            return objectMapper.writeValueAsString(testResults);
        } catch (Exception e) {
            log.error("Failed to serialize test results: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Update question statistics after submission completes.
     */
    @Transactional
    public void updateQuestionStatistics(Submission submission) {
        Long questionId = submission.getQuestionId();

        QuestionStatistics stats = statsRepository.findByQuestionId(questionId)
                .orElseGet(() -> QuestionStatistics.builder()
                        .questionId(questionId)
                        .totalSubmissions(0)
                        .acceptedSubmissions(0)
                        .build());

        stats.incrementSubmissions(
                submission.getVerdict() == SubmissionVerdict.ACCEPTED,
                submission.getRuntimeMs(),
                submission.getMemoryKb());

        statsRepository.save(stats);
        log.debug("Updated statistics for question {}: total={}, accepted={}",
                questionId, stats.getTotalSubmissions(), stats.getAcceptedSubmissions());
    }
}
