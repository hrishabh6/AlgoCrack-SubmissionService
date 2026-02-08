package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocrackentityservice.models.ReferenceSolution;
import com.hrishabh.algocracksubmissionservice.adapter.ExecutionAdapter;
import com.hrishabh.algocracksubmissionservice.dto.internal.BatchExecutionResult;
import com.hrishabh.algocracksubmissionservice.dto.internal.CodeBundle;
import com.hrishabh.algocracksubmissionservice.dto.internal.TestCaseInput;
import com.hrishabh.algocracksubmissionservice.exception.OracleMissingException;
import com.hrishabh.algocracksubmissionservice.repository.ReferenceSolutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for executing the oracle (reference solution) against testcases.
 * 
 * Key design principle: Execute oracle ONCE with ALL testcases in a single batch.
 * This prevents O(N) CXE calls and keeps p99 latency constant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OracleExecutionService {

    private final ReferenceSolutionRepository oracleRepository;
    private final ExecutionAdapter executionAdapter;

    /**
     * Execute the oracle for a question against all provided testcases.
     * Returns the expected outputs for each testcase.
     * 
     * This is a BATCH operation - single CXE call regardless of testcase count.
     * 
     * @param questionId The question to fetch oracle for
     * @param testcases All testcases to execute (batched)
     * @return Execution result with oracle outputs for each testcase
     * @throws OracleMissingException if no oracle exists for the question
     */
    public BatchExecutionResult executeOracle(Long questionId, List<TestCaseInput> testcases) {
        log.info("Executing oracle for question {} with {} testcases (batched)", 
                questionId, testcases.size());

        System.out.println("\n" + "#".repeat(60));
        System.out.println("[OracleExecutionService] executeOracle() CALLED");
        System.out.println("#".repeat(60));
        System.out.println("[OracleExecutionService] questionId: " + questionId);
        System.out.println("[OracleExecutionService] testcases count: " + testcases.size());

        // 1. Fetch oracle
        ReferenceSolution oracle = oracleRepository.findByQuestionId(questionId)
                .orElseThrow(() -> new OracleMissingException(questionId));

        System.out.println("[OracleExecutionService] Oracle Found:");
        System.out.println("    language: " + oracle.getLanguage());
        System.out.println("    sourceCode length: " + (oracle.getSourceCode() != null ? oracle.getSourceCode().length() : 0));
        System.out.println("    sourceCode preview (first 300 chars):");
        if (oracle.getSourceCode() != null) {
            System.out.println("--- ORACLE CODE START ---");
            System.out.println(oracle.getSourceCode().substring(0, Math.min(300, oracle.getSourceCode().length())));
            if (oracle.getSourceCode().length() > 300) {
                System.out.println("... [TRUNCATED]");
            }
            System.out.println("--- ORACLE CODE END ---");
        }

        // 2. Build code bundle for oracle execution
        String oracleExecutionId = "oracle-" + UUID.randomUUID().toString();
        
        CodeBundle oracleBundle = CodeBundle.builder()
                .executionId(oracleExecutionId)
                .code(oracle.getSourceCode())
                .language(oracle.getLanguage())
                .questionId(questionId)
                .userId(0L)  // Oracle is not user-specific
                .testcases(testcases)
                .build();

        System.out.println("[OracleExecutionService] Oracle CodeBundle built:");
        System.out.println("    executionId: " + oracleExecutionId);
        System.out.println("    language: " + oracleBundle.getLanguage());

        // 3. Execute via adapter (single batch call)
        System.out.println("\n[OracleExecutionService] Submitting oracle to ExecutionAdapter...");
        log.debug("[{}] Submitting oracle to execution adapter", oracleExecutionId);
        BatchExecutionResult result = executionAdapter.execute(oracleBundle);

        System.out.println("[OracleExecutionService] Oracle Execution Result:");
        System.out.println("    status: " + result.getStatus());
        System.out.println("    isSuccess: " + result.isSuccess());
        System.out.println("    outputs count: " + (result.getOutputs() != null ? result.getOutputs().size() : 0));
        if (result.getOutputs() != null) {
            for (int i = 0; i < result.getOutputs().size(); i++) {
                System.out.println("    output[" + i + "]: " + result.getOutputs().get(i).getOutput());
            }
        }
        if (result.getErrorMessage() != null) {
            System.out.println("    errorMessage: " + result.getErrorMessage());
        }
        if (result.getCompilationOutput() != null) {
            System.out.println("    compilationOutput: " + result.getCompilationOutput());
        }

        // 4. Validate oracle execution succeeded
        if (!result.isSuccess()) {
            log.error("[{}] Oracle execution failed: {}", oracleExecutionId, result.getStatus());
            System.out.println("[OracleExecutionService] ERROR: Oracle execution FAILED!");
            throw new RuntimeException("Oracle execution failed for question " + questionId + 
                    ": " + result.getErrorMessage());
        }

        log.info("[{}] Oracle execution completed successfully with {} outputs", 
                oracleExecutionId, result.getOutputs().size());
        System.out.println("#".repeat(60) + "\n");
        
        return result;
    }

    /**
     * Check if an oracle exists for a question.
     */
    public boolean hasOracle(Long questionId) {
        return oracleRepository.findByQuestionId(questionId).isPresent();
    }
}
