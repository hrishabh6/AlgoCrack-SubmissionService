package com.hrishabh.algocracksubmissionservice.complexity.client;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.PollResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.SubmitRequest;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.SubmitResponse;
import com.hrishabh.algocracksubmissionservice.helper.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CxeComplexityProfileClient {

    private final WebClient cxeWebClient;
    private final ComplexityProperties properties;

    @Value("${services.problem.internal-service-token:${INTERNAL_SERVICE_TOKEN:}}")
    private String internalServiceToken;

    public SubmitResponse submit(SubmitRequest request) {
        try {
            return cxeWebClient.post()
                    .uri("/api/v1/internal/execution/complexity-profiles")
                    .headers(this::applyInternalHeaders)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SubmitResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("CXE profile submit failed status={}", e.getStatusCode().value());
            if (e.getStatusCode().is4xxClientError()) {
                throw new CxeComplexityProfileException("PROFILE_VALIDATION_REJECTED", e.getMessage());
            }
            throw new CxeComplexityProfileTransportException("CXE profile submit transport failure", e);
        } catch (RuntimeException e) {
            if (e instanceof CxeComplexityProfileException profileEx) {
                throw profileEx;
            }
            throw new CxeComplexityProfileTransportException("CXE profile submit transport failure", e);
        }
    }

    public Optional<PollResponse> poll(String executionId) {
        try {
            PollResponse body = cxeWebClient.get()
                    .uri("/api/v1/internal/execution/complexity-profiles/{executionId}", executionId)
                    .headers(this::applyInternalHeaders)
                    .retrieve()
                    .bodyToMono(PollResponse.class)
                    .block();
            return Optional.ofNullable(body);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new CxeComplexityProfileException("PROFILE_VALIDATION_REJECTED", e.getMessage());
            }
            throw new CxeComplexityProfileTransportException("CXE profile poll transport failure", e);
        } catch (RuntimeException e) {
            if (e instanceof CxeComplexityProfileException profileEx) {
                throw profileEx;
            }
            throw new CxeComplexityProfileTransportException("CXE profile poll transport failure", e);
        }
    }

    public Optional<PollResponse> pollUntilTerminal(String executionId) {
        for (int attempt = 0; attempt < properties.getCxeProfilePollMaxAttempts(); attempt++) {
            Optional<PollResponse> response = poll(executionId);
            if (response.isEmpty()) {
                sleep();
                continue;
            }
            PollResponse poll = response.get();
            if ("COMPLETED".equals(poll.status()) || "FAILED".equals(poll.status())) {
                return response;
            }
            sleep();
        }
        throw new CxeComplexityProfileException("PROFILE_EXECUTION_FAILED", "profile poll timeout");
    }

    private void applyInternalHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(CurrentUser.INTERNAL_CALL_HEADER, "true");
        if (internalServiceToken != null && !internalServiceToken.isBlank()) {
            headers.set("X-Internal-Service-Token", internalServiceToken);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(properties.getCxeProfilePollIntervalMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CxeComplexityProfileException("PROFILE_EXECUTION_FAILED", "interrupted");
        }
    }
}
