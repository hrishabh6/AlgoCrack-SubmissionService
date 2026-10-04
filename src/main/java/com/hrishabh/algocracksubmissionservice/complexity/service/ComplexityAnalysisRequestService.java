package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisRequestResponse;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import com.hrishabh.algocracksubmissionservice.complexity.support.ComplexityLanguageSupport;
import com.hrishabh.algocracksubmissionservice.complexity.support.SourceSha256Hasher;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComplexityAnalysisRequestService {

    private final ComplexitySubmissionAccessService submissionAccessService;
    private final ComplexityAnalysisRepository complexityAnalysisRepository;
    private final ComplexityAnalysisMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ComplexityAnalysisRequestResponse requestAnalysis(String submissionPublicId, String userId) {
        Submission submission = submissionAccessService.requireEligibleOwnedSubmission(submissionPublicId, userId);

        var existingActive = complexityAnalysisRepository.findBySubmissionIdAndActiveSlot(
                submission.getSubmissionId(), ComplexityAnalysis.ACTIVE_SLOT_VALUE);
        if (existingActive.isPresent()) {
            return mapper.toRequestResponse(existingActive.get(), true);
        }

        try {
            ComplexityAnalysis created = complexityAnalysisRepository.save(buildQueuedAnalysis(submission));
            eventPublisher.publishEvent(new ComplexityAnalysisAsyncTrigger.ComplexityAnalysisRequestedEvent(created.getAnalysisId()));
            return mapper.toRequestResponse(created, false);
        } catch (DataIntegrityViolationException ex) {
            ComplexityAnalysis active = complexityAnalysisRepository
                    .findBySubmissionIdAndActiveSlot(submission.getSubmissionId(), ComplexityAnalysis.ACTIVE_SLOT_VALUE)
                    .orElseThrow(() -> ex);
            return mapper.toRequestResponse(active, true);
        }
    }

    private static ComplexityAnalysis buildQueuedAnalysis(Submission submission) {
        return ComplexityAnalysis.builder()
                .analysisId(UUID.randomUUID().toString())
                .submissionId(submission.getSubmissionId())
                .ownerUserId(submission.getUserId())
                .questionId(submission.getQuestionId())
                .language(ComplexityLanguageSupport.normalizeStoredLanguage(submission.getLanguage()))
                .status(ComplexityProcessingStatus.QUEUED)
                .sourceSha256(SourceSha256Hasher.hash(submission.getCode()))
                .activeSlot(ComplexityAnalysis.ACTIVE_SLOT_VALUE)
                .build();
    }
}
