package com.hrishabh.algocracksubmissionservice.complexity.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.GeneratedCaseDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.BatchExecutionResult;
import com.hrishabh.algocracksubmissionservice.dto.internal.TestCaseInput;
import com.hrishabh.algocracksubmissionservice.service.OracleExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Trusted expected outputs for benchmark cases (correctness only — not a runtime baseline).
 */
@Service
@RequiredArgsConstructor
public class ComplexityBenchmarkOracleService {

    private final OracleExecutionService oracleExecutionService;
    private final ObjectMapper objectMapper;

    public List<OracleCaseOutput> expectedOutputsForCases(long questionId, List<GeneratedCaseDto> cases) {
        List<TestCaseInput> inputs = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            GeneratedCaseDto c = cases.get(i);
            String serialized = c.serializedInput() != null ? c.serializedInput() : c.input().toString();
            inputs.add(TestCaseInput.builder().index(i).input(serialized).build());
        }
        BatchExecutionResult batch = oracleExecutionService.executeOracle(questionId, inputs);
        if (batch.getStatus() != BatchExecutionResult.ExecutionStatus.SUCCESS || batch.getOutputs() == null) {
            throw new BenchmarkOracleException("PROFILE_GENERATION_FAILED", "oracle batch failed");
        }
        List<OracleCaseOutput> outputs = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            GeneratedCaseDto c = cases.get(i);
            var tc = batch.getOutputs().get(i);
            JsonNode expected = parseOutput(tc.getOutput());
            outputs.add(new OracleCaseOutput(c.caseId(), c.inputHash(), expected));
        }
        return outputs;
    }

    private JsonNode parseOutput(String output) {
        try {
            if (output == null) {
                return objectMapper.nullNode();
            }
            return objectMapper.readTree(output);
        } catch (Exception e) {
            throw new BenchmarkOracleException("PROFILE_GENERATION_FAILED", "oracle output parse failed");
        }
    }

    public record OracleCaseOutput(String caseId, String inputHash, JsonNode expectedOutput) {
    }

    public static class BenchmarkOracleException extends RuntimeException {
        private final String errorCode;

        public BenchmarkOracleException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
