package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.dto.internal.TestCaseInput;
import com.hrishabh.algocracksubmissionservice.exception.TooManyRequestsException;
import com.hrishabh.algocracksubmissionservice.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to enforce guardrails on RUN mode requests.
 * Critical Fix #4: Prevents compute abuse from RUN mode.
 * 
 * Guards against:
 * - Rate limiting (per IP)
 * - Excessive testcase count
 * - Oversized input payloads
 */
@Slf4j
@Service
public class RunGuardService {

    // Configuration (could be externalized to application.properties)
    private static final int MAX_TESTCASES_PER_RUN = 10;
    private static final int MAX_INPUT_SIZE_BYTES = 10_000;
    private static final int MAX_TOTAL_PAYLOAD_BYTES = 100_000;
    private static final int RATE_LIMIT_REQUESTS_PER_MINUTE = 30;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000;

    // Simple in-memory rate limiter (production should use Redis)
    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * Validate a RUN request against all guardrails.
     * 
     * @param testcases The testcases in the request
     * @param clientIp  The client IP address
     * @throws TooManyRequestsException if rate limit exceeded
     * @throws ValidationException      if validation fails
     */
    public void validateRunRequest(List<TestCaseInput> testcases, String clientIp) {
        // 1. Rate limiting
        if (isRateLimited(clientIp)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            throw new TooManyRequestsException("RUN rate limit exceeded. Please wait before trying again.");
        }

        // 2. Testcase count cap
        if (testcases == null || testcases.isEmpty()) {
            throw new ValidationException("At least one testcase is required");
        }

        if (testcases.size() > MAX_TESTCASES_PER_RUN) {
            throw new ValidationException(
                    "Maximum " + MAX_TESTCASES_PER_RUN + " testcases per RUN. Got: " + testcases.size());
        }

        // 3. Input size cap per testcase
        int totalPayloadSize = 0;
        for (int i = 0; i < testcases.size(); i++) {
            TestCaseInput tc = testcases.get(i);
            String input = tc.getInput();
            if (input == null) {
                throw new ValidationException("Testcase " + i + " has null input");
            }

            int inputSize = input.length();
            if (inputSize > MAX_INPUT_SIZE_BYTES) {
                throw new ValidationException(
                        "Testcase " + i + " input too large. Max: " + MAX_INPUT_SIZE_BYTES +
                                " bytes, got: " + inputSize);
            }
            totalPayloadSize += inputSize;
        }

        // 4. Total payload size cap
        if (totalPayloadSize > MAX_TOTAL_PAYLOAD_BYTES) {
            throw new ValidationException(
                    "Total payload too large. Max: " + MAX_TOTAL_PAYLOAD_BYTES +
                            " bytes, got: " + totalPayloadSize);
        }

        // Record this request for rate limiting
        recordRequest(clientIp);

        log.debug("RUN request validated: {} testcases, {} bytes from IP {}",
                testcases.size(), totalPayloadSize, clientIp);
    }

    /**
     * Check if an IP is rate limited.
     */
    private boolean isRateLimited(String clientIp) {
        if (clientIp == null) {
            return false; // Don't block if we can't identify the client
        }

        RateLimitEntry entry = rateLimitMap.get(clientIp);
        if (entry == null) {
            return false;
        }

        // Check if window expired
        long now = System.currentTimeMillis();
        if (now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
            // Window expired, reset
            rateLimitMap.remove(clientIp);
            return false;
        }

        return entry.requestCount.get() >= RATE_LIMIT_REQUESTS_PER_MINUTE;
    }

    /**
     * Record a request for rate limiting.
     */
    private void recordRequest(String clientIp) {
        if (clientIp == null) {
            return;
        }

        long now = System.currentTimeMillis();

        rateLimitMap.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStart > RATE_LIMIT_WINDOW_MS) {
                // New window
                return new RateLimitEntry(now, new AtomicInteger(1));
            } else {
                // Same window, increment count
                existing.requestCount.incrementAndGet();
                return existing;
            }
        });
    }

    /**
     * Clean up expired entries (should be called periodically).
     */
    public void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        rateLimitMap.entrySet().removeIf(entry -> now - entry.getValue().windowStart > RATE_LIMIT_WINDOW_MS);
    }

    /**
     * Rate limit entry for tracking requests per IP.
     */
    private static class RateLimitEntry {
        final long windowStart;
        final AtomicInteger requestCount;

        RateLimitEntry(long windowStart, AtomicInteger requestCount) {
            this.windowStart = windowStart;
            this.requestCount = requestCount;
        }
    }
}
