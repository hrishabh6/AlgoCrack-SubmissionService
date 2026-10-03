package com.hrishabh.algocracksubmissionservice.playground.service;

import com.hrishabh.algocracksubmissionservice.dto.ExecutionMode;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionRequest;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionResponse;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import com.hrishabh.algocracksubmissionservice.exception.ValidationException;
import com.hrishabh.algocracksubmissionservice.logging.RequestContext;
import com.hrishabh.algocracksubmissionservice.exception.TooManyRequestsException;
import com.hrishabh.algocracksubmissionservice.playground.config.PlaygroundProperties;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.PlaygroundRunRequest;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.PlaygroundRunResponse;
import com.hrishabh.algocracksubmissionservice.playground.exception.PlaygroundConflictException;
import com.hrishabh.algocracksubmissionservice.playground.metrics.PlaygroundMetrics;
import com.hrishabh.algocracksubmissionservice.service.CodeExecutionClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundRunService {

    private final PlaygroundProperties properties;
    private final PlaygroundWorkspaceService workspaceService;
    private final PlaygroundRunGuardService runGuardService;
    private final CodeExecutionClientService codeExecutionClientService;
    private final PlaygroundMetrics playgroundMetrics;

    public PlaygroundRunResponse run(String userId, PlaygroundRunRequest request) {
        if (!properties.isRunEnabled()) {
            throw new ValidationException("Playground run is not enabled");
        }

        String language = workspaceService.normalizeRunLanguage(request.language());
        String source = workspaceService.validateRunSource(request.sourceCode());
        String stdin = workspaceService.validateRunStdin(request.stdin());

        try {
            runGuardService.acquire(userId);
        } catch (PlaygroundConflictException e) {
            playgroundMetrics.recordRunRejection("concurrent_run");
            throw e;
        } catch (TooManyRequestsException e) {
            playgroundMetrics.recordRunRejection("rate_limit");
            throw e;
        }

        log.info("playground_execution_requested userId={} language={}", userId, language);
        String executionId = "playground-" + UUID.randomUUID();
        try {
            ExecutionRequest cxeRequest = ExecutionRequest.builder()
                    .submissionId(executionId)
                    .executionId(executionId)
                    .userId(userId)
                    .language(language)
                    .executionMode(ExecutionMode.PLAYGROUND.name())
                    .code(source)
                    .stdin(stdin)
                    .requestId(RequestContext.getRequestId())
                    .build();

            ExecutionResponse accepted;
            try {
                accepted = codeExecutionClientService.submitCode(cxeRequest);
            } catch (RuntimeException e) {
                if (e.getCause() instanceof WebClientResponseException wce
                        && wce.getStatusCode().value() == 400
                        && wce.getResponseBodyAsString().contains("EXECUTION_QUEUE_FULL")) {
                    throw new ValidationException("Execution queue is full");
                }
                throw e;
            }

            SubmissionStatusDto result = pollUntilDone(
                    accepted.getSubmissionId(), properties.getCxePollTimeoutSeconds());
            PlaygroundRunResponse response = mapResult(executionId, result);
            playgroundMetrics.recordRun(language, response.status());
            if ("INTERNAL_ERROR".equals(response.status())) {
                log.warn("playground_execution_failed userId={} executionId={}", userId, executionId);
            } else {
                log.info("playground_execution_completed userId={} executionId={} status={}",
                        userId, executionId, response.status());
            }
            return response;
        } catch (RuntimeException e) {
            playgroundMetrics.recordRunRejection("execution_error");
            log.warn("playground_execution_failed userId={} executionId={} reason={}",
                    userId, executionId, e.getClass().getSimpleName());
            throw e;
        } finally {
            runGuardService.release(userId);
        }
    }

    private SubmissionStatusDto pollUntilDone(String submissionId, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            SubmissionStatusDto status = codeExecutionClientService.getStatus(submissionId);
            if (status == null) {
                throw new ValidationException("Execution status unavailable");
            }
            String s = status.getStatus();
            if ("COMPLETED".equals(s) || "FAILED".equals(s)) {
                return codeExecutionClientService.getResults(submissionId);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ValidationException("Execution interrupted");
            }
        }
        throw new ValidationException("Execution timed out waiting for results");
    }

    private PlaygroundRunResponse mapResult(String executionId, SubmissionStatusDto dto) {
        String terminal = dto.getRawExecutionStatus();
        if (terminal == null && "FAILED".equals(dto.getStatus())) {
            terminal = "INTERNAL_ERROR";
        }
        if (terminal == null) {
            terminal = "INTERNAL_ERROR";
        }
        return new PlaygroundRunResponse(
                executionId,
                terminal,
                nullToEmpty(dto.getStdout()),
                nullToEmpty(dto.getStderr()),
                nullToEmpty(dto.getCompilationOutput()),
                dto.getRuntimeMs(),
                dto.getMemoryKb(),
                dto.getExitCode(),
                Boolean.TRUE.equals(dto.getOutputTruncated()));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
