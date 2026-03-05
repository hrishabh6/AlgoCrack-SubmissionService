package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

import java.util.List;

/**
 * DTO for question metadata received from ProblemService API.
 * Flattened — includes both QuestionMetadata and parent Question fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionMetadataApiDto {
    private Long id;
    private Long questionId;
    private String functionName;
    private String returnType;
    private String language;
    private String codeTemplate;
    private String testCaseFormat;
    private String executionStrategy;
    private List<String> paramTypes;
    private List<String> paramNames;
    private String mutationTarget;
    private String serializationStrategy;
    private String questionType;
    // From Question entity:
    private Boolean isOutputOrderMatters;
    private String nodeType;
    private String validationHints;
}
