package com.hrishabh.algocracksubmissionservice.progress.controller;

import com.hrishabh.algocracksubmissionservice.helper.CurrentUser;
import com.hrishabh.algocracksubmissionservice.progress.dto.ProgressApiDtos.RecalculateAllResponse;
import com.hrishabh.algocracksubmissionservice.progress.dto.ProgressApiDtos.RecalculateResponse;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import com.hrishabh.algocracksubmissionservice.progress.service.ProgressRecalculationService;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/progress")
@RequiredArgsConstructor
public class ProgressAdminController {

    private final ProgressRecalculationService progressRecalculationService;
    private final SubmissionRepository submissionRepository;

    @PostMapping("/recalculate/{userId}")
    public ResponseEntity<RecalculateResponse> recalculateUser(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @RequestHeader(value = CurrentUser.ROLE_HEADER, required = false) String roleHeader,
            @PathVariable String userId) {
        CurrentUser.requireAdmin(userIdHeader, roleHeader);
        UserProgress progress = progressRecalculationService.recalculateUser(userId);
        return ResponseEntity.ok(RecalculateResponse.builder()
                .userId(userId)
                .totalRankScore(progress.getTotalRankScore())
                .rankTierCode(progress.getRankTierCode())
                .build());
    }

    @PostMapping("/recalculate-all")
    public ResponseEntity<RecalculateAllResponse> recalculateAll(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @RequestHeader(value = CurrentUser.ROLE_HEADER, required = false) String roleHeader) {
        CurrentUser.requireAdmin(userIdHeader, roleHeader);
        int processed = 0;
        for (String userId : submissionRepository.findDistinctAcceptedUserIds()) {
            progressRecalculationService.recalculateUser(userId);
            processed++;
        }
        return ResponseEntity.ok(RecalculateAllResponse.builder().usersProcessed(processed).build());
    }

    @GetMapping("/rebuilds/{runId}")
    public ResponseEntity<Void> getRebuildRun(@PathVariable long runId) {
        return ResponseEntity.notFound().build();
    }
}
