package com.hrishabh.algocracksubmissionservice.dto.internal;

import com.hrishabh.algocrackentityservice.models.Language;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Internal DTO representing code + testcases for execution.
 * CXE-agnostic - the ExecutionAdapter translates this to CXE-specific format.
 * 
 * This decouples the Submission Service from CXE DTOs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeBundle {

    /**
     * Unique identifier for this execution (for tracking/logging).
     */
    private String executionId;

    /**
     * The source code to execute.
     */
    private String code;

    /**
     * Programming language.
     */
    private Language language;

    /**
     * Question ID (for metadata lookup).
     */
    private Long questionId;

    /**
     * User ID (optional, 0 for RUN mode).
     */
    private String userId;

    /**
     * All testcases to execute in a single batch.
     * CXE executes all testcases in one invocation.
     */
    private List<TestCaseInput> testcases;

    /**
     * Question metadata for code execution.
     */
    private QuestionMetadataBundle metadata;

    /**
     * Execution intent (RUN or SUBMIT) for logging/metrics.
     */
    private ExecutionIntent intent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionMetadataBundle {
        private String fullyQualifiedPackageName;
        private String functionName;
        private String returnType;
        private List<Parameter> parameters;
        private List<String> customDataStructureNames;
        private String mutationTarget;
        private String serializationStrategy;
        private String questionType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameter {
        private String name;
        private String type;
    }
}
