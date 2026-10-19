package com.hrishabh.algocracksubmissionservice.adapter;

import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared CXE status poll. The budget stays above the Kubernetes Job deadline so a job
 * CXE is still allowed to finish is not abandoned by the client.
 */
public final class CxeExecutionPoll {

    public static final int POLL_INTERVAL_MS = 500;

    /**
     * CXE waits this long for a Kubernetes execution Job before treating it as failed.
     * {@code KUBERNETES_JOB_COMPLETION_TIMEOUT_SECONDS} defaults to 40.
     */
    public static final int JOB_DEADLINE_MS = 40_000;

    /** 50 seconds: past the 40-second job deadline, with one poll interval of slack. */
    public static final int MAX_POLL_ATTEMPTS = 100;

    private CxeExecutionPoll() {
    }

    public static int budgetMs() {
        return MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS;
    }

    public static SubmissionStatusDto poll(
            String submissionId,
            Supplier<SubmissionStatusDto> statusCall,
            Function<String, SubmissionStatusDto> resultsCall,
            PollWait wait) {
        try {
            for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
                SubmissionStatusDto status = statusCall.get();
                if (status != null
                        && ("COMPLETED".equals(status.getStatus()) || "FAILED".equals(status.getStatus()))) {
                    return resultsCall.apply(submissionId);
                }
                wait.await(POLL_INTERVAL_MS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Polling interrupted", interrupted);
        }
        throw new RuntimeException("Execution timeout after " + (budgetMs() / 1000) + " seconds");
    }

    @FunctionalInterface
    public interface PollWait {
        void await(int millis) throws InterruptedException;
    }
}
