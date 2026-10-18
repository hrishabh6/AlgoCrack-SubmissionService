package com.hrishabh.algocracksubmissionservice.adapter;

import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import com.hrishabh.algocracksubmissionservice.dto.internal.BatchExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CxeCompilationStatusTest {

    private final CxeExecutionAdapter adapter = new CxeExecutionAdapter(null, null, null);

    @Test
    void javacStderrStaysACompilationError() {
        String stderr = "Main.java:12: error: ';' expected\n            int x = ;\n";
        SubmissionStatusDto status = SubmissionStatusDto.builder()
                .errorMessage("COMPILATION_ERROR")
                .compilationOutput(stderr)
                .build();

        BatchExecutionResult result = BatchExecutionResult.builder()
                .status(adapter.determineStatus(status))
                .compilationOutput(status.getCompilationOutput())
                .errorMessage(status.getErrorMessage())
                .build();

        assertEquals(BatchExecutionResult.ExecutionStatus.COMPILATION_ERROR, result.getStatus());
        assertTrue(result.isCompilationError());
        assertTrue(result.getCompilationOutput().contains("error: ';' expected"));
    }

    @Test
    void missingRunnerDependenciesStayAnInfrastructureFailure() {
        SubmissionStatusDto status = SubmissionStatusDto.builder()
                .errorMessage("INTERNAL_ERROR")
                .compilationOutput("Error: Runner dependencies are missing from the javac/java classpath.")
                .build();

        assertEquals(BatchExecutionResult.ExecutionStatus.INTERNAL_ERROR, adapter.determineStatus(status));
    }
}
