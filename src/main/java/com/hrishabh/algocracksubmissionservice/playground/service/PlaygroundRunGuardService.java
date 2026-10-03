package com.hrishabh.algocracksubmissionservice.playground.service;

import com.hrishabh.algocracksubmissionservice.exception.TooManyRequestsException;
import com.hrishabh.algocracksubmissionservice.playground.config.PlaygroundProperties;
import com.hrishabh.algocracksubmissionservice.playground.exception.PlaygroundConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class PlaygroundRunGuardService {

    private static final long RATE_WINDOW_MS = 60_000;

    private final PlaygroundProperties properties;
    private final Map<String, AtomicInteger> activeRuns = new ConcurrentHashMap<>();
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public void acquire(String userId) {
        AtomicInteger active = activeRuns.computeIfAbsent(userId, ignored -> new AtomicInteger(0));
        if (active.incrementAndGet() > 1) {
            active.decrementAndGet();
            throw new PlaygroundConflictException("A playground run is already in progress");
        }
        if (isRateLimited(userId)) {
            active.decrementAndGet();
            throw new TooManyRequestsException("Playground run rate limit exceeded");
        }
        recordRun(userId);
    }

    public void release(String userId) {
        AtomicInteger active = activeRuns.get(userId);
        if (active != null) {
            active.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    private boolean isRateLimited(String userId) {
        long now = System.currentTimeMillis();
        RateWindow window = rateWindows.get(userId);
        if (window == null || now - window.windowStartMs > RATE_WINDOW_MS) {
            return false;
        }
        return window.count >= properties.getMaxRunsPerMinute();
    }

    public void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        rateWindows.entrySet().removeIf(e -> now - e.getValue().windowStartMs > RATE_WINDOW_MS);
        activeRuns.entrySet().removeIf(e -> e.getValue().get() <= 0);
    }

    private void recordRun(String userId) {
        long now = System.currentTimeMillis();
        rateWindows.compute(userId, (key, existing) -> {
            if (existing == null || now - existing.windowStartMs > RATE_WINDOW_MS) {
                return new RateWindow(now, 1);
            }
            existing.count++;
            return existing;
        });
    }

    private static final class RateWindow {
        long windowStartMs;
        int count;

        RateWindow(long windowStartMs, int count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }
}
