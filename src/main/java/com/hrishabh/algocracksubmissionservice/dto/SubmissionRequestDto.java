package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

/**
 * API Request DTO for code submission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRequestDto {

    private Long userId;
    private Long questionId;
    private String language;
    private String code;

    // Optional client metadata
    private String ipAddress;
    private String userAgent;
}
