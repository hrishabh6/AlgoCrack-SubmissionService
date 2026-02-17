package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocrackentityservice.models.*;
import com.hrishabh.algocracksubmissionservice.dto.*;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import com.hrishabh.algocracksubmissionservice.repository.QuestionMetadataRepository;
import com.hrishabh.algocracksubmissionservice.repository.TestcaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import com.hrishabh.algocracksubmissionservice.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    /**
     * Create a new submission and trigger async processing.
     */
    @Transactional
    public Submission createAndProcess(SubmissionRequestDto request) {
        log.info("Creating submission for user {} question {}", request.getUserId(), request.getQuestionId());
        // Generate UUID for external reference
        String submissionId = UUID.randomUUID().toString();
        // Get user and question references
        User user = userRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
        Question question = entityManager.getReference(Question.class, request.getQuestionId());
        // Create submission entity
        Submission submission = Submission.builder()
                .submissionId(submissionId)
                .user(user)
                .question(question)
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
    public List<SubmissionDetailDto> getUserSubmissions(String userId, int page, int size) {
        return submissionRepository.findByUser_UserIdOrderByQueuedAtDesc(userId, PageRequest.of(page, size))
                .getContent()
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
