package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityStaticFinding;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityStaticFindingRepository;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.JavaStaticAnalyzer;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticFindingDraft;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplexityStaticAnalysisPipeline {

    private final ComplexityAnalysisRepository analysisRepository;
    private final ComplexityStaticFindingRepository findingRepository;
    private final SubmissionRepository submissionRepository;
    private final ProblemServiceClient problemServiceClient;
    private final JavaStaticAnalyzer staticAnalyzer;
    private final JdkKnowledgeBase knowledgeBase;
    private final ComplexityAnalysisJsonSupport jsonSupport;

    @Transactional
    public boolean processQueuedAnalysis(String analysisPublicId) {
        Optional<ComplexityAnalysis> optional = analysisRepository.findByAnalysisId(analysisPublicId);
        if (optional.isEmpty()) {
            return false;
        }
        ComplexityAnalysis analysis = optional.get();
        if (analysis.getStatus() != ComplexityProcessingStatus.QUEUED || analysis.getActiveSlot() == null) {
            return false;
        }

        analysis.setStatus(ComplexityProcessingStatus.STATIC_ANALYZING);
        if (analysis.getStartedAt() == null) {
            analysis.setStartedAt(LocalDateTime.now());
        }
        analysisRepository.save(analysis);

        Submission submission = submissionRepository.findBySubmissionId(analysis.getSubmissionId())
                .orElseThrow(() -> new IllegalStateException("Submission missing for analysis " + analysisPublicId));

        QuestionMetadataApiDto metadata = fetchMetadataSafely(analysis.getQuestionId());
        StaticAnalysisResult result = staticAnalyzer.analyze(submission.getCode(), metadata);

        findingRepository.deleteByAnalysisId(analysis.getAnalysisId());
        persistFindings(analysis.getAnalysisId(), result.findings());

        applyResult(analysis, result);
        analysis.setStatus(ComplexityProcessingStatus.COMPLETED);
        analysis.setCompletedAt(LocalDateTime.now());
        analysis.setActiveSlot(null);
        analysisRepository.save(analysis);
        return true;
    }

    private QuestionMetadataApiDto fetchMetadataSafely(Long questionId) {
        try {
            return problemServiceClient.getMetadata(questionId, "JAVA");
        } catch (Exception ex) {
            log.debug("Question metadata unavailable for complexity analysis questionId={}", questionId, ex);
            return null;
        }
    }

    private void applyResult(ComplexityAnalysis analysis, StaticAnalysisResult result) {
        analysis.setResultKind(result.resultKind());
        analysis.setAnalyzerVersion(JavaStaticAnalyzer.ANALYZER_VERSION);
        analysis.setConfidenceModelVersion(JavaStaticAnalyzer.CONFIDENCE_MODEL_VERSION);
        analysis.setKnowledgeBaseVersion(knowledgeBase.version());
        analysis.setErrorCode(result.errorCode());

        if (result.timeExpression() != null) {
            analysis.setTimeExpression(ComplexityExprSimplifier.toExpressionString(result.timeExpression()));
            analysis.setTimeBigO(ComplexityExprSimplifier.toBigOString(result.timeExpression()));
            analysis.setTimeBoundBasis(result.timeBoundBasis().name());
            analysis.setTimeConfidence(result.timeConfidence());
        }
        if (result.spaceExpression() != null) {
            analysis.setSpaceExpression(ComplexityExprSimplifier.toExpressionString(result.spaceExpression()));
            analysis.setSpaceBigO(ComplexityExprSimplifier.toBigOString(result.spaceExpression()));
            analysis.setSpaceConfidence(result.spaceConfidence());
        }
        analysis.setLimitationsJson(jsonSupport.writeStringList(result.limitations()));
        analysis.setVariableDefinitionsJson(jsonSupport.writeMap(result.variables()));
    }

    private void persistFindings(String analysisId, List<StaticFindingDraft> findings) {
        int count = 0;
        for (StaticFindingDraft draft : findings) {
            if (count >= 50) {
                break;
            }
            findingRepository.save(ComplexityStaticFinding.builder()
                    .analysisId(analysisId)
                    .category(draft.category())
                    .sourceStartLine(draft.startLine())
                    .sourceEndLine(draft.endLine())
                    .expression(draft.expression())
                    .certainty(draft.certainty())
                    .summary(draft.summary())
                    .build());
            count++;
        }
    }
}
