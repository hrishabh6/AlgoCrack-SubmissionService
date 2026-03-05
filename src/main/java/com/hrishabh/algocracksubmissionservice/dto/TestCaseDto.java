package com.hrishabh.algocracksubmissionservice.dto;

import lombok.*;

/**
 * DTO for testcase data received from ProblemService API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDto {
    private Long id;
    private Long questionId;
    private String input;
    private String expectedOutput;
    private Integer orderIndex;
    private String type;
}
