package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDto {
    private Long questionId;
    private String language;
    private String code;
}
