package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for submitting code to CodeExecutionService.
 * Matches the CXE API specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRequest {

    private String submissionId;
    private String userId;
    private Long questionId;
    private String language;
    private String code;
    private QuestionMetadata metadata;
    private List<Map<String, Object>> testCases;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionMetadata {
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
