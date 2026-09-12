package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.dto.ExecutionRequest;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionResponse;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import com.hrishabh.algocracksubmissionservice.logging.LoggingConstants;
import com.hrishabh.algocracksubmissionservice.logging.StructuredLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * HTTP client service for communicating with CodeExecutionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionClientService {

    private final StructuredLogger structuredLogger = new StructuredLogger(CodeExecutionClientService.class,
            "SubmissionService");

    private final WebClient cxeWebClient;

    /**
     * Submit code to CXE for execution.
     * Returns immediately with submission ID.
     */
    public ExecutionResponse submitCode(ExecutionRequest request) {
        structuredLogger.info("Submitting code to CXE",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXTERNAL_CALL,
                LoggingConstants.OPERATION, "cxe_submit",
                LoggingConstants.SUBMISSION_ID, request.getSubmissionId(),
                LoggingConstants.USER_ID, request.getUserId(),
                LoggingConstants.QUESTION_ID, request.getQuestionId(),
                LoggingConstants.LANGUAGE, request.getLanguage());

        try {
            return cxeWebClient.post()
                    .uri("/api/v1/execution/submit")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ExecutionResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            structuredLogger.error("CXE submit failed", e,
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                    LoggingConstants.TYPE, "Error",
                    LoggingConstants.OPERATION, "cxe_submit",
                    LoggingConstants.SUBMISSION_ID, request.getSubmissionId(),
                    LoggingConstants.HTTP_STATUS, e.getStatusCode().value());
            throw new RuntimeException("Failed to submit to CXE: " + e.getMessage(), e);
        }
    }

    /**
     * Get current status of a submission from CXE.
     */
    public SubmissionStatusDto getStatus(String submissionId) {
        structuredLogger.debug("Polling CXE status",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXTERNAL_CALL,
                LoggingConstants.OPERATION, "cxe_status",
                LoggingConstants.SUBMISSION_ID, submissionId);

        try {
            return cxeWebClient.get()
                    .uri("/api/v1/execution/status/{id}", submissionId)
                    .retrieve()
                    .bodyToMono(SubmissionStatusDto.class)
                    .block();
        } catch (WebClientResponseException e) {
            structuredLogger.error("CXE status check failed", e,
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                    LoggingConstants.TYPE, "Error",
                    LoggingConstants.OPERATION, "cxe_status",
                    LoggingConstants.SUBMISSION_ID, submissionId,
                    LoggingConstants.HTTP_STATUS, e.getStatusCode().value());
            throw new RuntimeException("Failed to get status from CXE: " + e.getMessage(), e);
        }
    }

    /**
     * Get full results of a completed submission from CXE.
     */
    public SubmissionStatusDto getResults(String submissionId) {
        structuredLogger.info("Fetching CXE results",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXTERNAL_CALL,
                LoggingConstants.OPERATION, "cxe_results",
                LoggingConstants.SUBMISSION_ID, submissionId);

        try {
            return cxeWebClient.get()
                    .uri("/api/v1/execution/results/{id}", submissionId)
                    .retrieve()
                    .bodyToMono(SubmissionStatusDto.class)
                    .block();
        } catch (WebClientResponseException e) {
            structuredLogger.error("CXE results fetch failed", e,
                    LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                    LoggingConstants.TYPE, "Error",
                    LoggingConstants.OPERATION, "cxe_results",
                    LoggingConstants.SUBMISSION_ID, submissionId,
                    LoggingConstants.HTTP_STATUS, e.getStatusCode().value());
            throw new RuntimeException("Failed to get results from CXE: " + e.getMessage(), e);
        }
    }
}
