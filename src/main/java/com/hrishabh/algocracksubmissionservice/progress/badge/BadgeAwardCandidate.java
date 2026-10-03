package com.hrishabh.algocracksubmissionservice.progress.badge;

public record BadgeAwardCandidate(
        String code,
        boolean earned,
        long progressCurrent,
        long progressTarget) {
}
