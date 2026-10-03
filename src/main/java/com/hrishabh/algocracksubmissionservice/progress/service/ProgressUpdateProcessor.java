package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.progress.model.ProgressOutboxStatus;
import com.hrishabh.algocracksubmissionservice.progress.repository.ProgressUpdateOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProgressUpdateProcessor {

    private final ProgressUpdateOutboxRepository progressUpdateOutboxRepository;
    private final ProgressOutboxEventHandler progressOutboxEventHandler;
    private final Clock clock;
    @Value("${gamification.progress.outbox-batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${gamification.progress.outbox-poll-ms:5000}")
    public void processPendingEvents() {
        LocalDateTime now = LocalDateTime.now(clock);
        progressUpdateOutboxRepository
                .findReady(ProgressOutboxStatus.PENDING, now, PageRequest.of(0, batchSize))
                .forEach(event -> progressOutboxEventHandler.process(event.getId()));
    }
}
