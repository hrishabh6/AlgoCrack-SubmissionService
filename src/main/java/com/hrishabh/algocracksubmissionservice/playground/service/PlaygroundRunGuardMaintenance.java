package com.hrishabh.algocracksubmissionservice.playground.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaygroundRunGuardMaintenance {

    private final PlaygroundRunGuardService runGuardService;

    @Scheduled(fixedDelayString = "${playground.guard-cleanup-ms:300000}")
    public void cleanupExpiredRateWindows() {
        runGuardService.cleanupExpiredEntries();
    }
}
