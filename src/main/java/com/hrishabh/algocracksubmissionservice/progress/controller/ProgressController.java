package com.hrishabh.algocracksubmissionservice.progress.controller;

import com.hrishabh.algocracksubmissionservice.helper.CurrentUser;
import com.hrishabh.algocracksubmissionservice.progress.dto.ProgressApiDtos.*;
import com.hrishabh.algocracksubmissionservice.progress.service.LeaderboardReadService;
import com.hrishabh.algocracksubmissionservice.progress.service.ProgressReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressReadService progressReadService;
    private final LeaderboardReadService leaderboardReadService;

    @GetMapping("/api/v1/progress/{userId}")
    public ResponseEntity<ProgressResponse> getProgress(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean includeLeaderboardPosition) {
        CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(progressReadService.getProgress(userId, includeLeaderboardPosition));
    }

    @GetMapping("/api/v1/progress/{userId}/badges")
    public ResponseEntity<UserBadgesResponse> getUserBadges(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @PathVariable String userId) {
        CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(progressReadService.getUserBadges(userId));
    }

    @GetMapping("/api/v1/progress/me/leaderboard-position")
    public ResponseEntity<LeaderboardPositionResponse> getMyLeaderboardPosition(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader) {
        String userId = CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(progressReadService.getLeaderboardPosition(userId));
    }

    @GetMapping("/api/v1/badges")
    public ResponseEntity<List<BadgeDefinitionDto>> listBadges(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader) {
        CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(progressReadService.listBadgeDefinitions());
    }

    @GetMapping("/api/v1/leaderboard")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        String userId = CurrentUser.require(userIdHeader);
        return ResponseEntity.ok(leaderboardReadService.getLeaderboard(page, size, userId));
    }
}
