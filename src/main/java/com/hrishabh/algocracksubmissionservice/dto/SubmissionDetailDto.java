package com.hrishabh.algocracksubmissionservice.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocrackentityservice.models.Submission;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Detailed submission response DTO.
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDetailDto {

    private static final ObjectMapper mapper = new ObjectMapper();

    private String submissionId;
    private String userId;
    private Long questionId;
    private String language;
    private String code;
    private String status;
    private String verdict;
    private Integer runtimeMs;
    private Integer memoryKb;
    private Integer passedTestCases;
    private Integer totalTestCases;
    private String errorMessage;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /**
     * Convert from entity to DTO.
     * Extracts passedTestCases/totalTestCases from testResults JSON.
     */
    public static SubmissionDetailDto fromEntity(Submission submission) {
        // Extract test case counts from testResults JSON
        int[] counts = extractTestCaseCounts(submission.getTestResults());

        return SubmissionDetailDto.builder()
                .submissionId(submission.getSubmissionId())
                .userId(submission.getUser() != null ? submission.getUser().getUserId() : null)
                .questionId(submission.getQuestion() != null ? submission.getQuestion().getId() : null)
                .language(submission.getLanguage())
                .code(submission.getCode())
                .status(submission.getStatus() != null ? submission.getStatus().name() : null)
                .verdict(submission.getVerdict() != null ? submission.getVerdict().name() : null)
                .runtimeMs(submission.getRuntimeMs())
                .memoryKb(submission.getMemoryKb())
                .passedTestCases(counts[0])
                .totalTestCases(counts[1])
                .errorMessage(submission.getErrorMessage())
                .queuedAt(submission.getQueuedAt())
                .startedAt(submission.getStartedAt())
                .completedAt(submission.getCompletedAt())
                .build();
    }

    /**
     * Extract [passedCount, totalCount] from testResults JSON.
     */
    private static int[] extractTestCaseCounts(String testResultsJson) {
        if (testResultsJson == null || testResultsJson.isEmpty()) {
            return new int[] { 0, 0 };
        }
        try {
            List<Map<String, Object>> results = mapper.readValue(
                    testResultsJson, new TypeReference<List<Map<String, Object>>>() {
                    });
            int total = results.size();
            int passed = (int) results.stream()
                    .filter(r -> Boolean.TRUE.equals(r.get("passed")))
                    .count();
            return new int[] { passed, total };
        } catch (Exception e) {
            log.warn("Failed to parse testResults JSON: {}", e.getMessage());
            return new int[] { 0, 0 };
        }
    }
}
