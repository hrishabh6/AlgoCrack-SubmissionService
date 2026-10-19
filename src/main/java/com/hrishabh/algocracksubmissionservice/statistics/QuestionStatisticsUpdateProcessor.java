package com.hrishabh.algocracksubmissionservice.statistics;

import com.hrishabh.algocracksubmissionservice.statistics.model.StatisticsOutboxStatus;
import com.hrishabh.algocracksubmissionservice.statistics.repository.QuestionStatisticsOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
public class QuestionStatisticsUpdateProcessor {

    static final int MAX_ATTEMPTS = 5;

    private final QuestionStatisticsOutboxRepository outboxRepository;
    private final QuestionStatisticsApplier applier;
    private final Clock clock;

    @Value("${statistics.outbox-batch-size:25}")
    private int batchSize;

    @Value("${statistics.retry-backoff-ms:25}")
    private long retryBackoffMs;

    public QuestionStatisticsUpdateProcessor(
            QuestionStatisticsOutboxRepository outboxRepository,
            QuestionStatisticsApplier applier,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.applier = applier;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${statistics.outbox-poll-ms:2000}")
    public void processPending() {
        LocalDateTime now = LocalDateTime.now(clock);
        outboxRepository.findReady(StatisticsOutboxStatus.PENDING, now, PageRequest.of(0, batchSize))
                .forEach(event -> processOne(event.getId()));
    }

    public void processOne(long outboxId) {
        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                applier.apply(outboxId);
                return;
            } catch (OptimisticLockingFailureException
                    | DataIntegrityViolationException
                    | PessimisticLockingFailureException ex) {
                lastConflict = ex;
                log.warn("Question statistics conflict for outbox {} attempt {}/{}: {}",
                        outboxId, attempt, MAX_ATTEMPTS, ex.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                sleepBeforeRetry(attempt);
            }
        }
        applier.recordConflict(outboxId, lastConflict);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(retryBackoffMs * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying statistics update", interrupted);
        }
    }
}
