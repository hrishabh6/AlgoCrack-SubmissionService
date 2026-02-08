package com.hrishabh.algocracksubmissionservice.controllers;

import com.hrishabh.algocrackentityservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.dto.*;
import com.hrishabh.algocracksubmissionservice.exception.TooManyRequestsException;
import com.hrishabh.algocracksubmissionservice.exception.ValidationException;
import com.hrishabh.algocracksubmissionservice.service.CustomExecutionService;
import com.hrishabh.algocracksubmissionservice.service.SubmissionService;
import com.hrishabh.algocracksubmissionservice.service.UnifiedExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for code submissions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final CustomExecutionService customExecutionService;
    private final UnifiedExecutionService unifiedExecutionService;

    /**
     * Submit code for official judging (async).
     * Returns immediately with submission ID.
     * 
     * @param request Submission request
     * @return Submission response with ID
     */
    @PostMapping
    public ResponseEntity<SubmissionResponseDto> submit(@RequestBody SubmissionRequestDto request) {
        Submission submission = submissionService.createAndProcess(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(SubmissionResponseDto.builder()
                        .submissionId(submission.getSubmissionId())
                        .status(submission.getStatus().name())
                        .message("Submission queued for processing")
                        .build());
    }

    /**
     * Run code for testing (synchronous).
     * Similar to LeetCode's "Run Code" button.
     * 
     * Uses DEFAULT testcases if no custom provided.
     * Returns RUN-specific verdicts (PASSED_RUN, FAILED_RUN, etc.)
     * 
     * @param request     Run request with code and optional custom testcases
     * @param httpRequest HTTP request for IP extraction
     * @return Run response with results
     */
    @PostMapping("/run")
    public ResponseEntity<RunResponseDto> run(
            @RequestBody RunRequestDto request,
            HttpServletRequest httpRequest) {

        String clientIp = extractClientIp(httpRequest);

        // ==================== DETAILED DEBUG LOGGING ====================
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[CONTROLLER] /run ENDPOINT - REQUEST RECEIVED FROM FRONTEND");
        System.out.println("=".repeat(80));
        System.out.println("[CONTROLLER] Client IP: " + clientIp);
        System.out.println("[CONTROLLER] Question ID: " + request.getQuestionId());
        System.out.println("[CONTROLLER] Language: " + request.getLanguage());
        System.out.println(
                "[CONTROLLER] Code Length: " + (request.getCode() != null ? request.getCode().length() : 0) + " chars");
        System.out.println("[CONTROLLER] Code Preview (first 500 chars):");
        System.out.println("--- CODE START ---");
        if (request.getCode() != null) {
            System.out.println(request.getCode().substring(0, Math.min(500, request.getCode().length())));
            if (request.getCode().length() > 500) {
                System.out.println("... [TRUNCATED]");
            }
        }
        System.out.println("--- CODE END ---");

        if (request.getCustomTestCases() != null && !request.getCustomTestCases().isEmpty()) {
            System.out.println("[CONTROLLER] Custom TestCases Count: " + request.getCustomTestCases().size());
            for (int i = 0; i < request.getCustomTestCases().size(); i++) {
                System.out.println(
                        "[CONTROLLER] TestCase[" + i + "].input: " + request.getCustomTestCases().get(i).getInput());
            }
        } else {
            System.out.println("[CONTROLLER] Custom TestCases: NONE (will use DEFAULT from DB)");
        }
        System.out.println("=".repeat(80) + "\n");
        // ==================== END DEBUG LOGGING ====================

        log.info("RUN request from IP: {} for question: {}", clientIp, request.getQuestionId());

        RunResponseDto response = unifiedExecutionService.executeRun(request, clientIp);

        // ==================== RESPONSE LOGGING ====================
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[CONTROLLER] /run ENDPOINT - RESPONSE TO FRONTEND");
        System.out.println("=".repeat(80));
        System.out.println("[CONTROLLER] Verdict: " + response.getVerdict());
        System.out.println("[CONTROLLER] Success: " + response.isSuccess());
        System.out.println("[CONTROLLER] Runtime: " + response.getRuntimeMs() + "ms");
        System.out.println("[CONTROLLER] Memory: " + response.getMemoryKb() + "KB");
        if (response.getCompilationOutput() != null) {
            System.out.println("[CONTROLLER] Compilation Output: " + response.getCompilationOutput());
        }
        if (response.getErrorMessage() != null) {
            System.out.println("[CONTROLLER] Error Message: " + response.getErrorMessage());
        }
        if (response.getTestCaseResults() != null) {
            System.out.println("[CONTROLLER] TestCase Results Count: " + response.getTestCaseResults().size());
            for (var tc : response.getTestCaseResults()) {
                System.out.println("[CONTROLLER] TestCase[" + tc.getIndex() + "]:");
                System.out.println("    passed=" + tc.getPassed());
                System.out.println("    actualOutput=" + tc.getActualOutput());
                System.out.println("    expectedOutput=" + tc.getExpectedOutput());
                System.out.println("    executionTimeMs=" + tc.getExecutionTimeMs());
                if (tc.getError() != null) {
                    System.out.println("    error=" + tc.getError());
                }
            }
        }
        System.out.println("=".repeat(80) + "\n");
        // ==================== END RESPONSE LOGGING ====================

        return ResponseEntity.ok(response);
    }

    /**
     * Get submission details by ID.
     */
    @GetMapping("/{submissionId}")
    public ResponseEntity<SubmissionDetailDto> getSubmission(@PathVariable String submissionId) {
        return submissionService.findBySubmissionId(submissionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get user's submission history.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SubmissionDetailDto>> getUserSubmissions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(submissionService.getUserSubmissions(userId, page, size));
    }

    /**
     * Execute code with custom test cases (no judging, no persistence).
     * Returns raw output for user to visually inspect correctness.
     * 
     * @deprecated Use /run instead, which supports both custom and default
     *             testcases
     *             with proper oracle-based comparison.
     */
    @Deprecated
    @PostMapping("/custom")
    public ResponseEntity<CustomExecutionResponseDto> executeCustom(
            @RequestBody CustomExecutionRequestDto request) {
        return ResponseEntity.ok(customExecutionService.executeCustomTests(request));
    }

    /**
     * Extract client IP from request (handles proxies).
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // First IP in the list is the original client
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ================== Exception Handlers ==================

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<String> handleRateLimitExceeded(TooManyRequestsException e) {
        log.warn("Rate limit exceeded: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidationError(ValidationException e) {
        log.warn("Validation failed: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
