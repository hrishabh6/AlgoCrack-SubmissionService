package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.models.*;
import com.hrishabh.algocracksubmissionservice.dto.*;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.repository.QuestionMetadataRepository;
import com.hrishabh.algocracksubmissionservice.repository.TestcaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main service for submission orchestration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionRepository submissionRepository;
    private final QuestionMetadataRepository questionMetadataRepository;
    private final TestcaseRepository testcaseRepository;
    private final SubmissionProcessingService processingService;

    /**
     * Create a new submission and trigger async processing.
     */
    @Transactional
    public Submission createAndProcess(SubmissionRequestDto request) {
        log.info("Creating submission for user {} question {}", request.getUserId(), request.getQuestionId());
        // Generate UUID for external reference
        String submissionId = UUID.randomUUID().toString();
        // Create submission entity with scalar references (no JPA entity lookup needed)
        Submission submission = Submission.builder()
                .submissionId(submissionId)
                .userId(request.getUserId())
                .questionId(request.getQuestionId())
                .language(request.getLanguage())
                .code(request.getCode())
                .status(SubmissionStatus.QUEUED)
                .queuedAt(LocalDateTime.now())
                .ipAddress(request.getIpAddress())
                .userAgent(request.getUserAgent())
                .build();
        // Save to database
        submission = submissionRepository.save(submission);
        log.info("Submission {} created with status QUEUED", submissionId);
        // Trigger async processing AFTER this transaction commits
        // This ensures the submission is visible in the database before async
        // processing starts
        final String submissionIdFinal = submissionId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.debug("Transaction committed, triggering async processing for: {}", submissionIdFinal);
                processingService.processSubmission(submissionIdFinal);
            }
        });
        return submission;
    }

    /**
     * Find submission by external UUID.
     */
    public Optional<SubmissionDetailDto> findBySubmissionId(String submissionId) {
        return submissionRepository.findBySubmissionId(submissionId)
                .map(SubmissionDetailDto::fromEntity);
    }

    /**
     * Get user's submissions with pagination.
     */
    public List<SubmissionDetailDto> getUserSubmissions(String userId, Long questionId, int page, int size) {
        Page<Submission> submissions;
        if (questionId != null) {
            submissions = submissionRepository.findByUserIdAndQuestionIdOrderByQueuedAtDesc(userId, questionId,
                    PageRequest.of(page, size));
        } else {
            submissions = submissionRepository.findByUserIdOrderByQueuedAtDesc(userId, PageRequest.of(page, size));
        }

        return submissions.getContent()
                .stream()
                .map(SubmissionDetailDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get question metadata for execution request.
     */
    public Optional<QuestionMetadata> getQuestionMetadata(Long questionId, Language language) {
        return questionMetadataRepository.findByQuestionIdAndLanguage(questionId, language);
    }

    /**
     * Get test cases for a question.
     */
    public List<TestCase> getTestCases(Long questionId) {
        return testcaseRepository.findByQuestionId(questionId);
    }
}
