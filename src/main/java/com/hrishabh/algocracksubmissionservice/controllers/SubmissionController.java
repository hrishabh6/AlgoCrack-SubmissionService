package com.hrishabh.algocracksubmissionservice.controllers;

import com.hrishabh.algocrackentityservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionDetailDto;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionRequestDto;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionResponseDto;
import com.hrishabh.algocracksubmissionservice.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for code submissions.
 */
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * Submit code for execution.
     * Returns immediately with submission ID.
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
}
