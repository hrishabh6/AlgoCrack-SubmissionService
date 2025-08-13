package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDto {
    private Long userId;
    private Long submissionId;
    private Long questionId;
    private String language;
    private String code;
}
