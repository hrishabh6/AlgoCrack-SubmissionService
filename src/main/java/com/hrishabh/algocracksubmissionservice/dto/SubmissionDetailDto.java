package com.hrishabh.algocracksubmissionservice.dto;

import com.hrishabh.algocrackentityservice.models.Submission;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Detailed submission response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDetailDto {

    private String submissionId;
    private Long userId;
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
     */
    public static SubmissionDetailDto fromEntity(Submission submission) {
        return SubmissionDetailDto.builder()
                .submissionId(submission.getSubmissionId())
                .userId(submission.getUser() != null ? submission.getUser().getId() : null)
                .questionId(submission.getQuestion() != null ? submission.getQuestion().getId() : null)
                .language(submission.getLanguage())
                .code(submission.getCode())
                .status(submission.getStatus() != null ? submission.getStatus().name() : null)
                .verdict(submission.getVerdict() != null ? submission.getVerdict().name() : null)
                .runtimeMs(submission.getRuntimeMs())
                .memoryKb(submission.getMemoryKb())
                .passedTestCases(submission.getPassedTestCases())
                .totalTestCases(submission.getTotalTestCases())
                .errorMessage(submission.getErrorMessage())
                .queuedAt(submission.getQueuedAt())
                .startedAt(submission.getStartedAt())
                .completedAt(submission.getCompletedAt())
                .build();
    }
}
