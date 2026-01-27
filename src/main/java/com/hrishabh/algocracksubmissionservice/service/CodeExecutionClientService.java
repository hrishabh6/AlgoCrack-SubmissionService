package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.dto.ExecutionRequest;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionResponse;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
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

    private final WebClient cxeWebClient;

    /**
     * Submit code to CXE for execution.
     * Returns immediately with submission ID.
     */
    public ExecutionResponse submitCode(ExecutionRequest request) {
        log.info("Submitting code to CXE for submission: {}", request.getSubmissionId());

        try {
            return cxeWebClient.post()
                    .uri("/api/v1/execution/submit")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ExecutionResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("CXE submit failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to submit to CXE: " + e.getMessage(), e);
        }
    }

    /**
     * Get current status of a submission from CXE.
     */
    public SubmissionStatusDto getStatus(String submissionId) {
        log.debug("Polling CXE status for: {}", submissionId);

        try {
            return cxeWebClient.get()
                    .uri("/api/v1/execution/status/{id}", submissionId)
                    .retrieve()
                    .bodyToMono(SubmissionStatusDto.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("CXE status check failed for {}: {}", submissionId, e.getMessage());
            throw new RuntimeException("Failed to get status from CXE: " + e.getMessage(), e);
        }
    }

    /**
     * Get full results of a completed submission from CXE.
     */
    public SubmissionStatusDto getResults(String submissionId) {
        log.info("Getting full results from CXE for: {}", submissionId);

        try {
            return cxeWebClient.get()
                    .uri("/api/v1/execution/results/{id}", submissionId)
                    .retrieve()
                    .bodyToMono(SubmissionStatusDto.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("CXE results fetch failed for {}: {}", submissionId, e.getMessage());
            throw new RuntimeException("Failed to get results from CXE: " + e.getMessage(), e);
        }
    }
}
