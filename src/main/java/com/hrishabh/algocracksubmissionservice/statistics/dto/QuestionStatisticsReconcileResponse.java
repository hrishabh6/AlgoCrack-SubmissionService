package com.hrishabh.algocracksubmissionservice.statistics.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QuestionStatisticsReconcileResponse {
    Long questionId;
    int totalSubmissions;
    int acceptedSubmissions;
    Long version;
}
