package com.hrishabh.algocracksubmissionservice.statistics;

import com.hrishabh.algocracksubmissionservice.statistics.model.QuestionStatisticsOutbox;
import com.hrishabh.algocracksubmissionservice.statistics.model.StatisticsOutboxStatus;
import com.hrishabh.algocracksubmissionservice.statistics.repository.QuestionStatisticsOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class QuestionStatisticsOutboxService {

    private final QuestionStatisticsOutboxRepository outboxRepository;
    private final Clock clock;

    public QuestionStatisticsOutboxService(QuestionStatisticsOutboxRepository outboxRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    /**
     * Enqueue in the caller's transaction so the row commits atomically with the verdict.
     */
    @Transactional
    public long enqueue(String submissionId, Long questionId) {
        return outboxRepository.findBySubmissionId(submissionId)
                .map(QuestionStatisticsOutbox::getId)
                .orElseGet(() -> outboxRepository.saveAndFlush(QuestionStatisticsOutbox.builder()
                        .submissionId(submissionId)
                        .questionId(questionId)
                        .status(StatisticsOutboxStatus.PENDING)
                        .attempts(0)
                        .nextAttemptAt(LocalDateTime.now(clock))
                        .build()).getId());
    }
}
