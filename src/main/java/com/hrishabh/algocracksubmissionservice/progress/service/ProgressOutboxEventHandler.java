package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.progress.model.ProgressOutboxStatus;
import com.hrishabh.algocracksubmissionservice.progress.model.ProgressUpdateOutbox;
import com.hrishabh.algocracksubmissionservice.progress.repository.ProgressUpdateOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressOutboxEventHandler {

    private final ProgressUpdateOutboxRepository progressUpdateOutboxRepository;
    private final ProgressRecalculationService progressRecalculationService;
    private final Clock clock;

    @Transactional
    public void process(long outboxId) {
        ProgressUpdateOutbox event = progressUpdateOutboxRepository.findById(outboxId).orElse(null);
        if (event == null || event.getStatus() != ProgressOutboxStatus.PENDING) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            progressRecalculationService.recalculateUser(event.getUserId());
            event.setStatus(ProgressOutboxStatus.PROCESSED);
            event.setLastError(null);
        } catch (RuntimeException ex) {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(truncate(ex.getMessage()));
            event.setNextAttemptAt(now.plusSeconds(Math.min(300, 5L * event.getAttempts())));
            log.warn("Progress outbox failed for submission {} user {}: {}",
                    event.getSourceId(), event.getUserId(), ex.getMessage());
        }
        progressUpdateOutboxRepository.save(event);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
