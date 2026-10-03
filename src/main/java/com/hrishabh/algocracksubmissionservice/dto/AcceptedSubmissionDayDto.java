package com.hrishabh.algocracksubmissionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/** An accepted problem solve and the calendar date on which it completed. */
@Data
@AllArgsConstructor
public class AcceptedSubmissionDayDto {
    private Long questionId;
    private LocalDate submissionDate;
}
