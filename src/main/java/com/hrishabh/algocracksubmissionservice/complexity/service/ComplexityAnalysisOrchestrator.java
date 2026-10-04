package com.hrishabh.algocracksubmissionservice.complexity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityBenchmarkRunPersister;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityBenchmarkOracleService;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityBenchmarkOracleService.BenchmarkOracleException;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityBenchmarkOracleService.OracleCaseOutput;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityProfileCorrelationValidator;
import com.hrishabh.algocracksubmissionservice.complexity.benchmark.ComplexityProfileCorrelationValidator.ProfileContractException;
import com.hrishabh.algocracksubmissionservice.complexity.client.CxeComplexityProfileClient;
import com.hrishabh.algocracksubmissionservice.complexity.client.CxeComplexityProfileException;
import com.hrishabh.algocracksubmissionservice.complexity.client.CxeComplexityProfileTransportException;
import com.hrishabh.algocracksubmissionservice.complexity.config.ComplexityProperties;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.CasesRequest;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.CasesResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.GeneratedCaseDto;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.ProfileMetadataResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.ParameterDto;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.PollResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.ProfileCaseRequest;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.ProfileCaseResult;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.QuestionMetadataDto;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.SubmitRequest;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.SubmitResponse;
import com.hrishabh.algocracksubmissionservice.complexity.metrics.ComplexityMetrics;
import com.hrishabh.algocracksubmissionservice.complexity.inference.DynamicGrowthInferenceEngine;
import com.hrishabh.algocracksubmissionservice.complexity.inference.DynamicGrowthInferenceResult;
import com.hrishabh.algocracksubmissionservice.complexity.inference.GrowthCandidateFamily;
import com.hrishabh.algocracksubmissionservice.complexity.inference.TimingObservation;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityBenchmarkRun;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityStaticFinding;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ComplexityHybridConfidenceModel.ConfidenceInputs;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ComplexityHybridConfidenceModel;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ComplexityReconciliationEngine;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ReconciliationInput;
import com.hrishabh.algocracksubmissionservice.complexity.reconciliation.ReconciliationOutcome;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityAnalysisRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityBenchmarkRunRepository;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityStaticFindingRepository;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.JavaStaticAnalyzer;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticFindingDraft;
import com.hrishabh.algocracksubmissionservice.complexity.support.ComplexityAnalysisFingerprint;
import com.hrishabh.algocracksubmissionservice.complexity.support.ComplexityVersionConstants;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplexityAnalysisOrchestrator {

    private final ComplexityAnalysisRepository analysisRepository;
    private final ComplexityStaticFindingRepository findingRepository;
    private final ComplexityBenchmarkRunRepository benchmarkRunRepository;
    private final SubmissionRepository submissionRepository;
    private final ProblemServiceClient problemServiceClient;
    private final JavaStaticAnalyzer staticAnalyzer;
    private final JdkKnowledgeBase knowledgeBase;
    private final ComplexityAnalysisJsonSupport jsonSupport;
    private final ComplexityProperties properties;
    private final ComplexityProfileCorrelationValidator correlationValidator;
    private final ComplexityBenchmarkOracleService oracleService;
    private final CxeComplexityProfileClient profileClient;
    private final DynamicGrowthInferenceEngine inferenceEngine;
    private final ComplexityReconciliationEngine reconciliationEngine;
    private final ComplexityHybridConfidenceModel confidenceModel;
    private final ComplexityAnalysisLeaseService leaseService;
    private final ComplexityBenchmarkRunPersister benchmarkRunPersister;
    private final ObjectMapper objectMapper;
    private final ComplexityMetrics complexityMetrics;

    public void advance(String analysisPublicId) {
        ComplexityAnalysis analysis = analysisRepository.findByAnalysisId(analysisPublicId).orElse(null);
        if (analysis == null || analysis.getActiveSlot() == null) {
            return;
        }
        if (analysis.getStatus() == ComplexityProcessingStatus.COMPLETED
                || analysis.getStatus() == ComplexityProcessingStatus.FAILED) {
            return;
        }
        if (!leaseService.tryClaim(analysisPublicId)) {
            analysis = analysisRepository.findByAnalysisId(analysisPublicId).orElse(null);
            if (analysis == null || !leaseService.holdsLease(analysis)) {
                return;
            }
        } else {
            analysis = analysisRepository.findByAnalysisId(analysisPublicId).orElseThrow();
        }
        if (analysis.getStatus() == ComplexityProcessingStatus.BENCHMARKING) {
            leaseService.renew(analysisPublicId);
            analysis = analysisRepository.findByAnalysisId(analysisPublicId).orElseThrow();
        }

        switch (analysis.getStatus()) {
            case QUEUED -> startStatic(analysisPublicId);
            case STATIC_ANALYZING -> afterStatic(analysisPublicId);
            case BENCHMARK_PREPARING -> prepareBenchmark(analysisPublicId);
            case BENCHMARK_QUEUED -> submitBenchmark(analysisPublicId);
            case BENCHMARKING -> pollBenchmark(analysisPublicId);
            case RECONCILING -> reconcileAndComplete(analysisPublicId);
            default -> {
            }
        }
    }

    private void startStatic(String analysisPublicId) {
        if (leaseService.claimQueuedForStaticAnalysis(analysisPublicId) == 0) {
            return;
        }
        ComplexityAnalysis analysis = analysisRepository.findByAnalysisId(analysisPublicId).orElseThrow();
        if (analysis.getStartedAt() == null) {
            analysis.setStartedAt(LocalDateTime.now());
        }
        Submission submission = submissionRepository.findBySubmissionId(analysis.getSubmissionId()).orElseThrow();
        QuestionMetadataApiDto metadata = fetchMetadataSafely(analysis.getQuestionId());
        StaticAnalysisResult result = staticAnalyzer.analyze(submission.getCode(), metadata);
        findingRepository.deleteByAnalysisId(analysis.getAnalysisId());
        persistFindings(analysis.getAnalysisId(), result.findings());
        applyStaticDraft(analysis, result);
        analysisRepository.save(analysis);
        afterStatic(analysisPublicId);
    }

    private void afterStatic(String analysisPublicId) {
        ComplexityAnalysis analysis = analysisRepository.findByAnalysisId(analysisPublicId).orElseThrow();
        if (!properties.isDynamicProfilingEnabled()) {
            finalizeStaticOnly(analysisPublicId);
            return;
        }
        analysis.setStatus(ComplexityProcessingStatus.BENCHMARK_PREPARING);
        analysisRepository.save(analysis);
        prepareBenchmark(analysisPublicId);
    }

    private void prepareBenchmark(String analysisPublicId) {
        ComplexityAnalysis analysis = leaseService.loadForLeaseWrite(analysisPublicId).orElse(null);
        if (analysis == null) {
            log.debug("prepareBenchmark skipped stale lease analysisId={}", analysisPublicId);
            return;
        }
        try {
            Optional<ProfileMetadataResponse> profileOpt = problemServiceClient.getActiveComplexityProfile(
                    analysis.getQuestionId(), analysis.getLanguage());
            if (profileOpt.isEmpty()) {
                finalizeWithBenchmarkLimitation(analysisPublicId, "PROFILE_UNAVAILABLE");
                return;
            }
            ProfileMetadataResponse profile = profileOpt.get();
            correlationValidator.validateProfileMetadata(profile, analysis.getQuestionId(), analysis.getLanguage());
            CasesResponse cases = problemServiceClient.generateComplexityProfileCases(
                    analysis.getQuestionId(),
                    new CasesRequest(analysis.getLanguage(), profile.profileVersion(), profile.profileHash()));
            correlationValidator.validateCasesResponse(cases, profile);
            for (GeneratedCaseDto caseDto : cases.cases()) {
                correlationValidator.validateGeneratedCase(caseDto, profile);
            }
            oracleService.expectedOutputsForCases(analysis.getQuestionId(), cases.cases());

            analysis.setProfileId(profile.profileId());
            analysis.setProfileVersion(profile.profileVersion());
            analysis.setProfileHash(profile.profileHash());
            analysis.setGeneratorVersion(profile.generatorVersion());
            analysis.setHarnessVersion(ComplexityVersionConstants.DEFAULT_HARNESS_VERSION);
            analysis.setMeasurementPolicyVersion(ComplexityVersionConstants.MEASUREMENT_POLICY_VERSION);
            analysis.setInferenceVersion(ComplexityVersionConstants.DYNAMIC_INFERENCE_VERSION);
            analysis.setConfidenceModelVersion(ComplexityVersionConstants.HYBRID_CONFIDENCE_MODEL_VERSION);
            String fingerprint = buildFingerprint(analysis);
            Optional<ComplexityAnalysis> reusable = analysisRepository
                    .findFirstBySubmissionIdAndAnalysisFingerprintAndStatusOrderByCompletedAtDesc(
                            analysis.getSubmissionId(), fingerprint, ComplexityProcessingStatus.COMPLETED);
            if (reusable.isPresent() && !reusable.get().getAnalysisId().equals(analysis.getAnalysisId())) {
                ComplexityAnalysis prior = reusable.get();
                if (isCompatibleReuse(prior)) {
                    copyCompletedReuse(analysisPublicId, prior, fingerprint);
                    return;
                }
            }
            analysis.setAnalysisFingerprint(fingerprint);
            analysis.setStatus(ComplexityProcessingStatus.BENCHMARK_QUEUED);
            analysisRepository.save(analysis);
            submitBenchmark(analysisPublicId);
        } catch (ProfileContractException ex) {
            finalizeWithBenchmarkLimitation(analysisPublicId, ex.getErrorCode());
        } catch (BenchmarkOracleException ex) {
            finalizeWithBenchmarkLimitation(analysisPublicId, ex.getErrorCode());
        } catch (Exception ex) {
            log.warn("benchmark prepare failed analysisId={}", analysisPublicId, ex);
            finalizeWithBenchmarkLimitation(analysisPublicId, "PROFILE_GENERATION_FAILED");
        }
    }

    private void submitBenchmark(String analysisPublicId) {
        ComplexityAnalysis snapshot = leaseService.loadForLeaseWrite(analysisPublicId).orElse(null);
        if (snapshot == null) {
            return;
        }
        String executionId = snapshot.getProfileExecutionId();
        if (executionId == null || executionId.isBlank()) {
            executionId = stableProfileExecutionId(snapshot);
            final String stableExecutionId = executionId;
            leaseService.mutateIfLeaseHeld(analysisPublicId, analysis -> {
                analysis.setProfileExecutionId(stableExecutionId);
                analysis.setStatus(ComplexityProcessingStatus.BENCHMARKING);
            });
        } else {
            leaseService.mutateIfLeaseHeld(
                    analysisPublicId, analysis -> analysis.setStatus(ComplexityProcessingStatus.BENCHMARKING));
        }
        submitProfileToCxe(analysisPublicId, snapshot, executionId);
    }

    private void submitProfileToCxe(String analysisPublicId, ComplexityAnalysis analysis, String executionId) {
        try {
            SubmitRequest request = buildSubmitRequest(analysis, executionId);
            profileClient.submit(request);
        } catch (CxeComplexityProfileTransportException ex) {
            complexityMetrics.recordCxeTransportError("submit");
            log.warn("CXE profile submit transport failure analysisId={} executionId={}", analysisPublicId, executionId, ex);
        } catch (CxeComplexityProfileException ex) {
            finalizeWithBenchmarkLimitation(analysisPublicId, ex.getErrorCode());
        }
    }

    private void pollBenchmark(String analysisPublicId) {
        ComplexityAnalysis analysis = leaseService.loadForLeaseWrite(analysisPublicId).orElse(null);
        if (analysis == null) {
            return;
        }
        String executionId = analysis.getProfileExecutionId();
        if (executionId == null) {
            finalizeWithBenchmarkLimitation(analysisPublicId, "PROFILE_EXECUTION_FAILED");
            return;
        }
        try {
            Optional<PollResponse> pollOpt = profileClient.poll(executionId);
            if (pollOpt.isEmpty()) {
                submitProfileToCxe(analysisPublicId, analysis, executionId);
                return;
            }
            PollResponse poll = pollOpt.get();
            if (!"COMPLETED".equals(poll.status()) && !"FAILED".equals(poll.status())) {
                return;
            }
            if ("FAILED".equals(poll.status())) {
                finalizeWithBenchmarkLimitation(
                        analysisPublicId,
                        poll.errorCode() != null ? poll.errorCode() : "PROFILE_EXECUTION_FAILED");
                return;
            }
            analysis = leaseService.loadForLeaseWrite(analysisPublicId).orElse(null);
            if (analysis == null) {
                return;
            }
            ProfileMetadataResponse profile = reloadProfile(analysis);
            correlationValidator.validatePollResponse(poll, profile, analysis.getHarnessVersion());
            CasesResponse cases = problemServiceClient.generateComplexityProfileCases(
                    analysis.getQuestionId(),
                    new CasesRequest(analysis.getLanguage(), profile.profileVersion(), profile.profileHash()));
            benchmarkRunPersister.upsertTerminalPoll(
                    analysis.getAnalysisId(),
                    poll,
                    cases.cases(),
                    analysis.getHarnessVersion(),
                    analysis.getMeasurementPolicyVersion());
            if (poll.profilerRuntimeVersion() != null) {
                analysis.setProfilerRuntimeVersion(poll.profilerRuntimeVersion());
            }
            analysis.setStatus(ComplexityProcessingStatus.RECONCILING);
            analysisRepository.save(analysis);
            reconcileAndComplete(analysisPublicId);
        } catch (ProfileContractException ex) {
            finalizeWithBenchmarkLimitation(analysisPublicId, ex.getErrorCode());
        } catch (CxeComplexityProfileTransportException ex) {
            complexityMetrics.recordCxeTransportError("poll");
            log.warn("CXE profile poll transport failure analysisId={} executionId={}", analysisPublicId, executionId, ex);
        } catch (CxeComplexityProfileException ex) {
            finalizeWithBenchmarkLimitation(analysisPublicId, ex.getErrorCode());
        }
    }

    private void reconcileAndComplete(String analysisPublicId) {
        ComplexityAnalysis analysis = leaseService.loadForLeaseWrite(analysisPublicId).orElse(null);
        if (analysis == null) {
            log.debug("reconcile skipped stale lease analysisId={}", analysisPublicId);
            return;
        }
        Submission submission = submissionRepository.findBySubmissionId(analysis.getSubmissionId()).orElseThrow();
        QuestionMetadataApiDto metadata = fetchMetadataSafely(analysis.getQuestionId());
        StaticAnalysisResult staticResult = staticAnalyzer.analyze(submission.getCode(), metadata);

        List<TimingObservation> observations = loadObservations(analysis.getAnalysisId());
        int usableCount = (int) observations.stream().filter(TimingObservation::usableForInference).count();
        DynamicGrowthInferenceResult dynamic = inferenceEngine.infer(observations);
        GrowthCandidateFamily dynamicFamily = dynamic.family();
        boolean staticDynamicAgree = GrowthCandidateFamily.fromStaticBigO(
                staticResult.timeExpression() != null
                        ? ComplexityExprSimplifier.toBigOString(staticResult.timeExpression())
                        : null).map(f -> f == dynamicFamily).orElse(false);

        List<String> limitations = new ArrayList<>();
        if (analysis.getLimitationsJson() != null) {
            limitations.addAll(jsonSupport.readStringList(analysis.getLimitationsJson()));
        }
        ReconciliationOutcome outcome = reconciliationEngine.reconcile(new ReconciliationInput(
                staticResult,
                dynamic,
                dynamicFamily,
                true,
                false,
                limitations));

        analysis.setResultKind(outcome.resultKind());
        analysis.setTimeBigO(outcome.timeBigO());
        analysis.setTimeExpression(outcome.timeExpression());
        analysis.setTimeConfidence(confidenceModel.timeConfidence(new ConfidenceInputs(
                outcome.resultKind(),
                staticResult,
                dynamic,
                usableCount,
                usableCount,
                staticDynamicAgree,
                false)));
        analysis.setInferenceVersion(ComplexityVersionConstants.DYNAMIC_INFERENCE_VERSION);
        analysis.setConfidenceModelVersion(ComplexityVersionConstants.HYBRID_CONFIDENCE_MODEL_VERSION);
        limitations.addAll(outcome.limitations());
        analysis.setLimitationsJson(jsonSupport.writeStringList(limitations));
        analysis.setAnalysisFingerprint(buildFingerprint(analysis));
        analysis.setStatus(ComplexityProcessingStatus.COMPLETED);
        analysis.setCompletedAt(LocalDateTime.now());
        analysis.setActiveSlot(null);
        analysis.setLeaseOwner(null);
        analysis.setLeaseExpiresAt(null);
        analysisRepository.save(analysis);
        int ineligible = observations.size() - usableCount;
        complexityMetrics.recordBenchmarkUsability(usableCount, ineligible);
        complexityMetrics.recordInferenceOutcome(
                dynamicFamily.name(),
                dynamicFamily == GrowthCandidateFamily.INCONCLUSIVE ? "INCONCLUSIVE" : "RESOLVED");
        recordCompletion(analysis, staticResult.resultKind().name());
    }

    private SubmitRequest buildSubmitRequest(ComplexityAnalysis analysis, String executionId) {
        Submission submission = submissionRepository.findBySubmissionId(analysis.getSubmissionId()).orElseThrow();
        ProfileMetadataResponse profile = reloadProfile(analysis);
        CasesResponse cases = problemServiceClient.generateComplexityProfileCases(
                analysis.getQuestionId(),
                new CasesRequest(analysis.getLanguage(), profile.profileVersion(), profile.profileHash()));
        correlationValidator.validateCasesResponse(cases, profile);
        List<OracleCaseOutput> oracleOutputs = oracleService.expectedOutputsForCases(
                analysis.getQuestionId(), cases.cases());
        Map<String, OracleCaseOutput> oracleByCaseId = new HashMap<>();
        for (OracleCaseOutput output : oracleOutputs) {
            oracleByCaseId.put(output.caseId(), output);
        }
        QuestionMetadataApiDto metadata = problemServiceClient.getMetadata(analysis.getQuestionId(), analysis.getLanguage());
        List<ProfileCaseRequest> caseRequests = new ArrayList<>();
        for (GeneratedCaseDto c : cases.cases()) {
            OracleCaseOutput oracle = oracleByCaseId.get(c.caseId());
            caseRequests.add(new ProfileCaseRequest(
                    c.caseId(),
                    c.caseIdentity(),
                    c.profileCode(),
                    c.profileVersion(),
                    c.profileHash(),
                    c.generatorVersion(),
                    c.variant(),
                    c.sizeVector(),
                    c.seed(),
                    c.input(),
                    c.inputHash(),
                    oracle.expectedOutput(),
                    profile.warmups(),
                    profile.measuredRepeats()));
        }
        QuestionMetadataDto questionMetadata = toCxeMetadata(metadata, analysis.getQuestionId());
        return new SubmitRequest(
                executionId,
                analysis.getSubmissionId(),
                analysis.getQuestionId(),
                analysis.getLanguage(),
                submission.getCode(),
                questionMetadata,
                profile.profileCode(),
                profile.profileVersion(),
                profile.profileHash(),
                analysis.getHarnessVersion(),
                caseRequests);
    }

    private ProfileMetadataResponse reloadProfile(ComplexityAnalysis analysis) {
        return problemServiceClient.getActiveComplexityProfile(analysis.getQuestionId(), analysis.getLanguage())
                .orElseThrow(() -> new ProfileContractException("PROFILE_UNAVAILABLE", "profile missing"));
    }

    private List<TimingObservation> loadObservations(String analysisId) {
        List<TimingObservation> observations = new ArrayList<>();
        for (ComplexityBenchmarkRun run : benchmarkRunRepository.findByAnalysisId(analysisId)) {
            Map<String, Integer> sizeVector = jsonSupport.readIntegerMap(run.getSizeVectorJson());
            boolean usable = "SUCCESS".equals(run.getOutcome())
                    && run.isOutputValidated()
                    && run.getMedianElapsedNs() != null
                    && run.getSampleCount() > 0;
            observations.add(new TimingObservation(
                    run.getCaseId(),
                    run.getVariant(),
                    sizeVector,
                    "n",
                    sizeVector.getOrDefault("n", sizeVector.values().stream().findFirst().orElse(0)),
                    run.getMedianElapsedNs() != null ? run.getMedianElapsedNs() : 0,
                    run.getMadElapsedNs() != null ? run.getMadElapsedNs() : 0,
                    run.isOutputValidated(),
                    usable));
        }
        Optional<String> resolved = DynamicGrowthInferenceEngine.resolvePrimaryDimension(observations);
        if (resolved.isEmpty()) {
            return observations;
        }
        String dim = resolved.get();
        List<TimingObservation> normalized = new ArrayList<>();
        for (TimingObservation o : observations) {
            long primarySize = o.sizeVector().getOrDefault(dim, 0);
            normalized.add(new TimingObservation(
                    o.caseId(), o.variant(), o.sizeVector(), dim, primarySize,
                    o.medianElapsedNs(), o.madElapsedNs(), o.outputValidated(), o.usableForInference()));
        }
        return normalized;
    }

    private void finalizeStaticOnly(String analysisPublicId) {
        ComplexityAnalysis analysis = leaseService.loadForLeaseWrite(analysisPublicId).orElse(null);
        if (analysis == null) {
            log.debug("finalizeStaticOnly skipped stale lease analysisId={}", analysisPublicId);
            return;
        }
        analysis.setResultKind(analysis.getResultKind());
        analysis.setAnalysisFingerprint(buildFingerprint(analysis));
        analysis.setStatus(ComplexityProcessingStatus.COMPLETED);
        analysis.setCompletedAt(LocalDateTime.now());
        analysis.setActiveSlot(null);
        analysis.setLeaseOwner(null);
        analysis.setLeaseExpiresAt(null);
        analysisRepository.save(analysis);
        recordCompletion(analysis, analysis.getResultKind() != null ? analysis.getResultKind().name() : "STATIC_ONLY");
    }

    private void finalizeWithBenchmarkLimitation(String analysisPublicId, String code) {
        ComplexityAnalysis analysis = leaseService.loadForLeaseWrite(analysisPublicId).orElse(null);
        if (analysis == null) {
            log.debug("finalizeWithBenchmarkLimitation skipped stale lease analysisId={}", analysisPublicId);
            return;
        }
        Submission submission = submissionRepository.findBySubmissionId(analysis.getSubmissionId()).orElseThrow();
        StaticAnalysisResult staticResult = staticAnalyzer.analyze(submission.getCode(), fetchMetadataSafely(analysis.getQuestionId()));
        applyStaticDraft(analysis, staticResult);
        List<String> limitations = jsonSupport.readStringList(analysis.getLimitationsJson());
        limitations.add(code);
        ReconciliationOutcome outcome = reconciliationEngine.reconcile(new ReconciliationInput(
                staticResult,
                DynamicGrowthInferenceResult.inconclusive(code, List.of(code)),
                GrowthCandidateFamily.INCONCLUSIVE,
                true,
                "PROFILE_INFRASTRUCTURE_UNAVAILABLE".equals(code),
                limitations));
        analysis.setResultKind(outcome.resultKind());
        analysis.setTimeBigO(outcome.timeBigO());
        analysis.setTimeExpression(outcome.timeExpression());
        analysis.setTimeConfidence(outcome.timeConfidence());
        analysis.setLimitationsJson(jsonSupport.writeStringList(outcome.limitations()));
        analysis.setErrorCode(code);
        analysis.setAnalysisFingerprint(buildFingerprint(analysis));
        analysis.setStatus(ComplexityProcessingStatus.COMPLETED);
        analysis.setCompletedAt(LocalDateTime.now());
        analysis.setActiveSlot(null);
        analysis.setLeaseOwner(null);
        analysis.setLeaseExpiresAt(null);
        analysisRepository.save(analysis);
        complexityMetrics.recordBenchmarkLimitation(code);
        recordCompletion(analysis, staticResult.resultKind().name());
    }

    private void applyStaticDraft(ComplexityAnalysis analysis, StaticAnalysisResult result) {
        analysis.setResultKind(result.resultKind());
        analysis.setAnalyzerVersion(JavaStaticAnalyzer.ANALYZER_VERSION);
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
        analysis.setStatus(ComplexityProcessingStatus.STATIC_ANALYZING);
    }

    private void copyCompletedReuse(String analysisPublicId, ComplexityAnalysis source, String fingerprint) {
        ComplexityAnalysis target = leaseService.loadForLeaseWrite(analysisPublicId).orElse(null);
        if (target == null) {
            log.debug("copyCompletedReuse skipped stale lease analysisId={}", analysisPublicId);
            return;
        }
        target.setAnalysisFingerprint(fingerprint);
        target.setReusedFromAnalysisId(source.getAnalysisId());
        target.setResultKind(source.getResultKind());
        target.setTimeBigO(source.getTimeBigO());
        target.setTimeExpression(source.getTimeExpression());
        target.setTimeBoundBasis(source.getTimeBoundBasis());
        target.setTimeConfidence(source.getTimeConfidence());
        target.setSpaceBigO(source.getSpaceBigO());
        target.setSpaceExpression(source.getSpaceExpression());
        target.setSpaceConfidence(source.getSpaceConfidence());
        target.setLimitationsJson(source.getLimitationsJson());
        target.setVariableDefinitionsJson(source.getVariableDefinitionsJson());
        target.setStatus(ComplexityProcessingStatus.COMPLETED);
        target.setCompletedAt(LocalDateTime.now());
        target.setActiveSlot(null);
        target.setLeaseOwner(null);
        target.setLeaseExpiresAt(null);
        analysisRepository.save(target);
        complexityMetrics.recordCacheReuse();
        recordCompletion(target, target.getResultKind() != null ? target.getResultKind().name() : "UNKNOWN");
    }

    private void recordCompletion(ComplexityAnalysis analysis, String staticOutcome) {
        complexityMetrics.recordCompleted(
                analysis.getResultKind() != null ? analysis.getResultKind().name() : "UNKNOWN",
                staticOutcome);
    }

    private boolean isCompatibleReuse(ComplexityAnalysis prior) {
        if (!properties.isDynamicProfilingEnabled()) {
            return true;
        }
        if (prior.getResultKind() == ComplexityResultKind.STATIC_ONLY
                && (prior.getProfileHash() == null || prior.getProfileHash().isBlank())) {
            return false;
        }
        return prior.getResultKind() == ComplexityResultKind.HYBRID
                || prior.getResultKind() == ComplexityResultKind.EMPIRICAL_ONLY
                || prior.getResultKind() == ComplexityResultKind.STATIC_ONLY;
    }

    private static String stableProfileExecutionId(ComplexityAnalysis analysis) {
        return ComplexityVersionConstants.PROFILE_EXECUTION_ID_PREFIX + analysis.getAnalysisId();
    }

    private String interpretationPath(ComplexityAnalysis analysis) {
        if (!properties.isDynamicProfilingEnabled()) {
            return ComplexityVersionConstants.INTERPRETATION_STATIC_ONLY;
        }
        if (analysis.getProfileHash() == null || analysis.getProfileHash().isBlank()) {
            return ComplexityVersionConstants.INTERPRETATION_STATIC_ONLY;
        }
        return ComplexityVersionConstants.INTERPRETATION_DYNAMIC_BENCHMARK;
    }

    private String buildFingerprint(ComplexityAnalysis analysis) {
        return ComplexityAnalysisFingerprint.compute(new ComplexityAnalysisFingerprint.FingerprintInput(
                analysis.getSourceSha256(),
                analysis.getAnalyzerVersion(),
                analysis.getInferenceVersion() != null
                        ? analysis.getInferenceVersion()
                        : ComplexityVersionConstants.DYNAMIC_INFERENCE_VERSION,
                analysis.getConfidenceModelVersion() != null
                        ? analysis.getConfidenceModelVersion()
                        : JavaStaticAnalyzer.CONFIDENCE_MODEL_VERSION,
                analysis.getKnowledgeBaseVersion(),
                analysis.getProfileHash(),
                analysis.getGeneratorVersion(),
                analysis.getHarnessVersion() != null ? analysis.getHarnessVersion() : ComplexityVersionConstants.DEFAULT_HARNESS_VERSION,
                analysis.getMeasurementPolicyVersion() != null
                        ? analysis.getMeasurementPolicyVersion()
                        : ComplexityVersionConstants.MEASUREMENT_POLICY_VERSION,
                analysis.getProfilerRuntimeVersion(),
                interpretationPath(analysis)));
    }

    private QuestionMetadataDto toCxeMetadata(QuestionMetadataApiDto metadata, long questionId) {
        List<ParameterDto> params = new ArrayList<>();
        if (metadata.getParamNames() != null && metadata.getParamTypes() != null) {
            for (int i = 0; i < metadata.getParamNames().size(); i++) {
                params.add(new ParameterDto(metadata.getParamNames().get(i), metadata.getParamTypes().get(i)));
            }
        }
        return new QuestionMetadataDto(
                "com.algocrack.solution.q" + questionId,
                metadata.getFunctionName(),
                metadata.getReturnType(),
                params,
                Map.of(),
                metadata.getQuestionType(),
                metadata.getIsOutputOrderMatters());
    }

    private QuestionMetadataApiDto fetchMetadataSafely(Long questionId) {
        try {
            return problemServiceClient.getMetadata(questionId, "JAVA");
        } catch (Exception ex) {
            return null;
        }
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
