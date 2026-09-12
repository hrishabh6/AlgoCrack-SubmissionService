package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.dto.ReferenceSolutionDto;
import com.hrishabh.algocracksubmissionservice.models.Language;
import com.hrishabh.algocracksubmissionservice.adapter.ExecutionAdapter;
import com.hrishabh.algocracksubmissionservice.dto.internal.BatchExecutionResult;
import com.hrishabh.algocracksubmissionservice.dto.internal.CodeBundle;
import com.hrishabh.algocracksubmissionservice.dto.internal.TestCaseInput;
import com.hrishabh.algocracksubmissionservice.exception.OracleMissingException;
import com.hrishabh.algocracksubmissionservice.logging.LoggingConstants;
import com.hrishabh.algocracksubmissionservice.logging.StructuredLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for executing the oracle (reference solution) against testcases.
 * 
 * Key design principle: Execute oracle ONCE with ALL testcases in a single
 * batch.
 * This prevents O(N) CXE calls and keeps p99 latency constant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OracleExecutionService {

    private final StructuredLogger structuredLogger = new StructuredLogger(OracleExecutionService.class, "SubmissionService");

    private final ProblemServiceClient problemServiceClient;
    private final ExecutionAdapter executionAdapter;

    /**
     * Execute the oracle for a question against all provided testcases.
     * Returns the expected outputs for each testcase.
     * 
     * This is a BATCH operation - single CXE call regardless of testcase count.
     * 
     * @param questionId The question to fetch oracle for
     * @param testcases  All testcases to execute (batched)
     * @return Execution result with oracle outputs for each testcase
     * @throws OracleMissingException if no oracle exists for the question
     */
    public BatchExecutionResult executeOracle(Long questionId, List<TestCaseInput> testcases) {
        structuredLogger.info("Oracle execution started",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                LoggingConstants.OPERATION, "oracle_execute",
                LoggingConstants.QUESTION_ID, questionId,
                LoggingConstants.TESTCASE_COUNT, testcases.size());

        // 1. Fetch oracle via ProblemService API
        ReferenceSolutionDto oracle;
        try {
            oracle = problemServiceClient.getOracle(questionId);
        } catch (Exception e) {
            structuredLogger.warn("Oracle fetch failed",
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                    LoggingConstants.TYPE, "Warn",
                    LoggingConstants.OPERATION, "oracle_fetch",
                    LoggingConstants.QUESTION_ID, questionId,
                    LoggingConstants.ERROR_MESSAGE, e.getMessage());
            throw new OracleMissingException(questionId);
        }
        if (oracle == null || oracle.getSourceCode() == null) {
            structuredLogger.warn("Oracle missing",
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                    LoggingConstants.TYPE, "Warn",
                    LoggingConstants.OPERATION, "oracle_fetch",
                    LoggingConstants.QUESTION_ID, questionId);
            throw new OracleMissingException(questionId);
        }

        structuredLogger.debug("Oracle found",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                LoggingConstants.OPERATION, "oracle_fetch",
                LoggingConstants.QUESTION_ID, questionId,
                LoggingConstants.LANGUAGE, oracle.getLanguage(),
                LoggingConstants.CODE_LENGTH, oracle.getSourceCode().length());

        // 2. Build code bundle for oracle execution
        String oracleExecutionId = "oracle-" + UUID.randomUUID().toString();

        CodeBundle oracleBundle = CodeBundle.builder()
                .executionId(oracleExecutionId)
                .code(oracle.getSourceCode())
                .language(Language.valueOf(oracle.getLanguage().toUpperCase()))
                .questionId(questionId)
                .userId("SYSTEM") // Oracle is not user-specific
                .testcases(testcases)
                .build();

        // 3. Execute via adapter (single batch call)
        structuredLogger.debug("Oracle bundle built",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                LoggingConstants.OPERATION, "oracle_bundle_built",
                LoggingConstants.EXECUTION_ID, oracleExecutionId,
                LoggingConstants.QUESTION_ID, questionId,
                LoggingConstants.LANGUAGE, oracleBundle.getLanguage(),
                LoggingConstants.TESTCASE_COUNT, testcases.size());

        BatchExecutionResult result = executionAdapter.execute(oracleBundle);

        // 4. Validate oracle execution succeeded
        if (!result.isSuccess()) {
            structuredLogger.error("Oracle execution failed",
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                    LoggingConstants.TYPE, "Error",
                    LoggingConstants.OPERATION, "oracle_execute",
                    LoggingConstants.EXECUTION_ID, oracleExecutionId,
                    LoggingConstants.QUESTION_ID, questionId,
                    LoggingConstants.STATUS, result.getStatus(),
                    "has_error", result.getErrorMessage() != null,
                    "has_compilation_output", result.getCompilationOutput() != null);
            throw new RuntimeException("Oracle execution failed for question " + questionId +
                    ": " + result.getErrorMessage());
        }

        structuredLogger.info("Oracle execution completed",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                LoggingConstants.OPERATION, "oracle_execute",
                LoggingConstants.EXECUTION_ID, oracleExecutionId,
                LoggingConstants.QUESTION_ID, questionId,
                LoggingConstants.STATUS, result.getStatus(),
                LoggingConstants.OUTPUT_COUNT, result.getOutputs() != null ? result.getOutputs().size() : 0,
                LoggingConstants.RUNTIME_MS, result.getTotalRuntimeMs(),
                LoggingConstants.MEMORY_KB, result.getPeakMemoryKb());

        return result;
    }

    /**
     * Check if an oracle exists for a question.
     */
    public boolean hasOracle(Long questionId) {
        try {
            ReferenceSolutionDto oracle = problemServiceClient.getOracle(questionId);
            return oracle != null && oracle.getSourceCode() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
