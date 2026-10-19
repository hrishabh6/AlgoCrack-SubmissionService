package com.hrishabh.algocracksubmissionservice.adapter;

import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CxeExecutionPollTest {

    @Test
    void pollBudgetStaysAboveTheKubernetesJobDeadline() {
        assertTrue(CxeExecutionPoll.budgetMs() > CxeExecutionPoll.JOB_DEADLINE_MS);
        assertEquals(50_000, CxeExecutionPoll.budgetMs());
    }

    @Test
    void pollAcceptsAJobThatFinishesAfterThirtySecondsButInsideTheJobDeadline() {
        AtomicInteger polls = new AtomicInteger();
        SubmissionStatusDto completed = SubmissionStatusDto.builder().status("COMPLETED").submissionId("job-1").build();

        SubmissionStatusDto result = CxeExecutionPoll.poll(
                "job-1",
                () -> {
                    int attempt = polls.incrementAndGet();
                    if (attempt >= 71) {
                        return completed;
                    }
                    return SubmissionStatusDto.builder().status("RUNNING").submissionId("job-1").build();
                },
                id -> completed,
                millis -> {
                });

        assertEquals("COMPLETED", result.getStatus());
        assertTrue(polls.get() >= 71);
    }

    @Test
    void sequentialUserAndOraclePollsEachGetAFullBudget() {
        SubmissionStatusDto completed = SubmissionStatusDto.builder().status("COMPLETED").build();
        for (int execution = 0; execution < 2; execution++) {
            AtomicInteger polls = new AtomicInteger();
            CxeExecutionPoll.poll(
                    "job-" + execution,
                    () -> polls.incrementAndGet() >= 71
                            ? completed
                            : SubmissionStatusDto.builder().status("RUNNING").build(),
                    id -> completed,
                    millis -> {
                    });
            assertTrue(polls.get() >= 71);
        }
    }

    @Test
    void pollFailsWhenTheJobDoesNotFinishInsideTheBudget() {
        RuntimeException timeout = assertThrows(RuntimeException.class, () -> CxeExecutionPoll.poll(
                "job-slow",
                () -> SubmissionStatusDto.builder().status("RUNNING").build(),
                id -> SubmissionStatusDto.builder().status("COMPLETED").build(),
                millis -> {
                }));
        assertTrue(timeout.getMessage().contains("50"));
    }
}
