package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.progress.model.ProgressEventType;
import com.hrishabh.algocracksubmissionservice.progress.model.ProgressOutboxStatus;
import com.hrishabh.algocracksubmissionservice.progress.model.ProgressUpdateOutbox;
import com.hrishabh.algocracksubmissionservice.progress.repository.ProgressUpdateOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
public class ProgressOutboxService {

    private final ProgressUpdateOutboxRepository progressUpdateOutboxRepository;
    private final Clock clock;

    public ProgressOutboxService(ProgressUpdateOutboxRepository progressUpdateOutboxRepository, Clock clock) {
        this.progressUpdateOutboxRepository = progressUpdateOutboxRepository;
        this.clock = clock;
    }

    @Transactional
    public void enqueueAcceptedSubmission(Submission submission) {
        if (submission.getVerdict() != SubmissionVerdict.ACCEPTED) {
            return;
        }
        try {
            progressUpdateOutboxRepository.save(ProgressUpdateOutbox.builder()
                    .eventType(ProgressEventType.ACCEPTED_SUBMISSION)
                    .sourceId(submission.getSubmissionId())
                    .userId(submission.getUserId())
                    .status(ProgressOutboxStatus.PENDING)
                    .nextAttemptAt(LocalDateTime.now(clock))
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Progress outbox already contains submission {}", submission.getSubmissionId());
        }
    }
}
