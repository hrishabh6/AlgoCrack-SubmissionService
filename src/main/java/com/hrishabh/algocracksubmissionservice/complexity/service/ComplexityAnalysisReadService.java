package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisDetailResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisHistoryResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisSummaryResponse;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityAnalysisNotFoundException;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityStaticFindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ComplexityAnalysisReadService {

    private final ComplexitySubmissionAccessService submissionAccessService;
    private final ComplexityAnalysisRepository complexityAnalysisRepository;
    private final ComplexityAnalysisMapper mapper;
    private final ComplexityProperties properties;
    private final ComplexityStaticFindingRepository findingRepository;

    @Transactional(readOnly = true)
    public ComplexityAnalysisHistoryResponse listAnalyses(String submissionPublicId, String userId, int page, int size) {
        submissionAccessService.requireOwnedSubmission(submissionPublicId, userId);
        int cappedSize = Math.min(Math.max(size, 1), properties.getMaxPageSize());
        Page<com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis> result =
                complexityAnalysisRepository.findBySubmissionIdAndOwnerUserIdOrderByRequestedAtDesc(
                        submissionPublicId, userId, PageRequest.of(page, cappedSize));
        List<ComplexityAnalysisSummaryResponse> items = result.getContent().stream()
                .map(mapper::toSummary)
                .toList();
        return new ComplexityAnalysisHistoryResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ComplexityAnalysisDetailResponse getAnalysis(
            String submissionPublicId, String analysisId, String userId) {
        submissionAccessService.requireOwnedSubmission(submissionPublicId, userId);
        var analysis = complexityAnalysisRepository.findByAnalysisIdAndSubmissionId(analysisId, submissionPublicId)
                .orElseThrow(ComplexityAnalysisNotFoundException::new);
        var findings = findingRepository.findByAnalysisIdOrderByIdAsc(analysis.getAnalysisId());
        return mapper.toDetail(analysis, analysis.getReusedFromAnalysisId() != null ? Boolean.TRUE : null, findings);
    }
}
