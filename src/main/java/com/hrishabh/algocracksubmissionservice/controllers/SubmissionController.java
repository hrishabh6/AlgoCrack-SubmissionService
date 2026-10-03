package com.hrishabh.algocracksubmissionservice.controllers;

import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.dto.*;
import com.hrishabh.algocracksubmissionservice.exception.TooManyRequestsException;
import com.hrishabh.algocracksubmissionservice.exception.ValidationException;
import com.hrishabh.algocracksubmissionservice.logging.LoggingConstants;
import com.hrishabh.algocracksubmissionservice.logging.StructuredLogger;
import com.hrishabh.algocracksubmissionservice.repository.QuestionStatisticsRepository;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.service.CustomExecutionService;
import com.hrishabh.algocracksubmissionservice.service.StreakCalculator;
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

    private final StructuredLogger structuredLogger = new StructuredLogger(SubmissionController.class, "SubmissionService");

    private final SubmissionService submissionService;
    private final CustomExecutionService customExecutionService;
    private final UnifiedExecutionService unifiedExecutionService;
    private final SubmissionRepository submissionRepository;
    private final QuestionStatisticsRepository questionStatisticsRepository;

    private static final int MAX_QUESTION_STATS_IDS = 200;

    /**
     * Submit code for official judging (async).
     * Returns immediately with submission ID.
     * 
     * @param request Submission request
     * @return Submission response with ID
     */
    @PostMapping
    public ResponseEntity<SubmissionResponseDto> submit(@RequestBody SubmissionRequestDto request) {
        structuredLogger.info("Submission request received",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.SUBMISSION,
                LoggingConstants.OPERATION, "submit",
                LoggingConstants.USER_ID, request.getUserId(),
                LoggingConstants.QUESTION_ID, request.getQuestionId(),
                LoggingConstants.LANGUAGE, request.getLanguage(),
                LoggingConstants.CODE_LENGTH, request.getCode() != null ? request.getCode().length() : 0);

        Submission submission = submissionService.createAndProcess(request);

        structuredLogger.info("Submission queued",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.SUBMISSION,
                LoggingConstants.OPERATION, "submit",
                LoggingConstants.USER_ID, request.getUserId(),
                LoggingConstants.QUESTION_ID, request.getQuestionId(),
                LoggingConstants.SUBMISSION_ID, submission.getSubmissionId(),
                LoggingConstants.STATUS, submission.getStatus().name());

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

        structuredLogger.info("Run request received",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                LoggingConstants.OPERATION, "run",
                LoggingConstants.CLIENT_IP, clientIp,
                LoggingConstants.QUESTION_ID, request.getQuestionId(),
                LoggingConstants.LANGUAGE, request.getLanguage(),
                LoggingConstants.CODE_LENGTH, request.getCode() != null ? request.getCode().length() : 0,
                LoggingConstants.TESTCASE_COUNT,
                request.getCustomTestCases() != null ? request.getCustomTestCases().size() : 0,
                "custom_testcases", request.getCustomTestCases() != null && !request.getCustomTestCases().isEmpty());

        RunResponseDto response = unifiedExecutionService.executeRun(request, clientIp);

        structuredLogger.info("Run response completed",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.EXECUTION,
                LoggingConstants.OPERATION, "run",
                LoggingConstants.CLIENT_IP, clientIp,
                LoggingConstants.QUESTION_ID, request.getQuestionId(),
                LoggingConstants.VERDICT, response.getVerdict(),
                LoggingConstants.STATUS, response.isSuccess() ? "SUCCESS" : "FAILED",
                LoggingConstants.RUNTIME_MS, response.getRuntimeMs(),
                LoggingConstants.MEMORY_KB, response.getMemoryKb(),
                LoggingConstants.TESTCASE_COUNT,
                response.getTestCaseResults() != null ? response.getTestCaseResults().size() : 0,
                "has_error", response.getErrorMessage() != null,
                "has_compilation_output", response.getCompilationOutput() != null);

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
            @PathVariable String userId,
            @RequestParam(required = false) Long questionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(submissionService.getUserSubmissions(userId, questionId, page, size));
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

    // ── Inter-Service APIs (Phase 7) ──────────────────────────────────

    /**
     * Get user submission statistics (solved counts by difficulty and language).
     * Called by ProblemService's UserProfileService.
     */
    @GetMapping("/stats/{userId}")
    public ResponseEntity<UserSubmissionStatsDto> getUserStats(@PathVariable String userId) {
        long easy = submissionRepository.countDistinctSolvedByUserIdAndDifficulty(userId, "EASY");
        long medium = submissionRepository.countDistinctSolvedByUserIdAndDifficulty(userId, "MEDIUM");
        long hard = submissionRepository.countDistinctSolvedByUserIdAndDifficulty(userId, "HARD");
        long total = submissionRepository.countDistinctSolvedByUserId(userId);

        List<Object[]> langRows = submissionRepository.countDistinctSolvedByUserIdGroupByLanguage(userId);
        List<UserSubmissionStatsDto.LanguageStat> languageStats = langRows.stream()
                .map(row -> UserSubmissionStatsDto.LanguageStat.builder()
                        .language((String) row[0])
                        .count((Long) row[1])
                        .build())
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(UserSubmissionStatsDto.builder()
                .totalSolved(total)
                .easySolved(easy)
                .mediumSolved(medium)
                .hardSolved(hard)
                .languageStats(languageStats)
                .build());
    }

    /**
     * Get distinct accepted question IDs solved by user.
     * Used by ProblemService to derive accurate difficulty distribution.
     */
    @GetMapping("/stats/{userId}/solved-question-ids")
    public ResponseEntity<List<Long>> getSolvedQuestionIds(@PathVariable String userId) {
        return ResponseEntity.ok(submissionRepository.findDistinctSolvedQuestionIdsByUserId(userId));
    }

    /**
     * Get the user's daily submission streak (current and longest).
     * Called by ProblemService's UserProfileService.
     */
    @GetMapping("/stats/{userId}/streak")
    public ResponseEntity<StreakDto> getStreak(@PathVariable String userId) {
        List<java.time.LocalDate> days = StreakCalculator.toLocalDates(
                submissionRepository.findDistinctSubmissionDatesByUserId(userId));
        return ResponseEntity.ok(StreakCalculator.calculate(days, java.time.LocalDate.now()));
    }

    /**
     * Aggregate submission counts for a batch of questions.
     * Called by ProblemService to populate acceptance rates for one page of problems.
     */
    @GetMapping("/question-stats")
    public ResponseEntity<List<QuestionStatsDto>> getQuestionStats(@RequestParam List<Long> ids) {
        if (ids.size() > MAX_QUESTION_STATS_IDS) {
            throw new ValidationException("At most " + MAX_QUESTION_STATS_IDS + " question ids are allowed");
        }
        return ResponseEntity.ok(questionStatisticsRepository.findByQuestionIdIn(ids).stream()
                .map(stats -> QuestionStatsDto.builder()
                        .questionId(stats.getQuestionId())
                        .totalSubmissions(stats.getTotalSubmissions())
                        .acceptedSubmissions(stats.getAcceptedSubmissions())
                        .build())
                .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Get user submission heatmap data.
     * Called by ProblemService's UserProfileService.
     */
    @GetMapping("/heatmap/{userId}")
    public ResponseEntity<HeatmapDataDto> getHeatmap(
            @PathVariable String userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDate fromDate;
        java.time.LocalDate toDate;

        if (from != null && to != null) {
            fromDate = java.time.LocalDate.parse(from);
            toDate = java.time.LocalDate.parse(to);
        } else if (year != null) {
            fromDate = java.time.LocalDate.of(year, 1, 1);
            toDate = java.time.LocalDate.of(year, 12, 31);
        } else {
            int currentYear = java.time.LocalDate.now().getYear();
            fromDate = java.time.LocalDate.of(currentYear, 1, 1);
            toDate = java.time.LocalDate.of(currentYear, 12, 31);
        }

        java.time.LocalDateTime fromDt = fromDate.atStartOfDay();
        java.time.LocalDateTime toDt = toDate.plusDays(1).atStartOfDay();

        List<Object[]> rows = submissionRepository.countSubmissionsGroupedByDateBetween(userId, fromDt, toDt);

        List<HeatmapDataDto.DayActivity> activity = rows.stream()
                .map(row -> HeatmapDataDto.DayActivity.builder()
                        .date(row[0].toString())
                        .count((Long) row[1])
                        .build())
                .collect(java.util.stream.Collectors.toList());

        long totalSubmissions = activity.stream().mapToLong(HeatmapDataDto.DayActivity::getCount).sum();

        return ResponseEntity.ok(HeatmapDataDto.builder()
                .year(year != null ? year : fromDate.getYear())
                .from(fromDate.toString())
                .to(toDate.toString())
                .activity(activity)
                .totalSubmissions(totalSubmissions)
                .totalActiveDays(activity.size())
                .build());
    }

    // ================== Exception Handlers ==================

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<String> handleRateLimitExceeded(TooManyRequestsException e) {
        structuredLogger.warn("Rate limit exceeded",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                LoggingConstants.TYPE, "Warn",
                LoggingConstants.ERROR_MESSAGE, e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidationError(ValidationException e) {
        structuredLogger.warn("Validation failed",
                LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
                LoggingConstants.TYPE, "Warn",
                LoggingConstants.ERROR_MESSAGE, e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
