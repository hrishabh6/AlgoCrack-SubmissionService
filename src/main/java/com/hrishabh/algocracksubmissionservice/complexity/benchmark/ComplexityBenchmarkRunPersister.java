package com.hrishabh.algocracksubmissionservice.complexity.benchmark;

import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.GeneratedCaseDto;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.PollResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.ProfileCaseResult;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityBenchmarkRun;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityBenchmarkRunRepository;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexityAnalysisJsonSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ComplexityBenchmarkRunPersister {

    private final ComplexityBenchmarkRunRepository benchmarkRunRepository;
    private final ComplexityAnalysisJsonSupport jsonSupport;
    private final ComplexityProfileCorrelationValidator correlationValidator;

    @Transactional
    public void upsertTerminalPoll(
            String analysisId,
            PollResponse poll,
            List<GeneratedCaseDto> expectedCases,
            String harnessVersion,
            String measurementPolicyVersion) {
        if (poll.cases() == null) {
            return;
        }
        Map<String, GeneratedCaseDto> expectedByIdentity = new HashMap<>();
        for (GeneratedCaseDto c : expectedCases) {
            expectedByIdentity.put(c.caseIdentity(), c);
        }
        for (ProfileCaseResult result : poll.cases()) {
            GeneratedCaseDto expected = expectedByIdentity.get(result.caseIdentity());
            if (expected == null) {
                throw new ComplexityProfileCorrelationValidator.ProfileContractException(
                        "PROFILE_HASH_DRIFT", "unknown case identity " + result.caseIdentity());
            }
            correlationValidator.validatePollCaseMatchesGenerated(expected, result);
            ComplexityBenchmarkRun row = benchmarkRunRepository
                    .findByAnalysisIdAndCaseIdentity(analysisId, result.caseIdentity())
                    .orElseGet(() -> ComplexityBenchmarkRun.builder()
                            .analysisId(analysisId)
                            .caseIdentity(result.caseIdentity())
                            .build());
            row.setCaseId(result.caseId());
            row.setVariant(result.variant() != null ? result.variant() : "DEFAULT");
            row.setSizeVectorJson(jsonSupport.writeIntegerMap(result.sizeVector()));
            row.setInputHash(expected.inputHash());
            row.setProfileVersion(result.profileVersion());
            row.setProfileHash(result.profileHash());
            row.setGeneratorVersion(result.generatorVersion());
            row.setHarnessVersion(harnessVersion);
            row.setMeasurementPolicyVersion(
                    poll.measurementPolicyVersion() != null ? poll.measurementPolicyVersion() : measurementPolicyVersion);
            row.setSampleCount(result.sampleCount());
            row.setWarmupCount(result.warmupCount());
            row.setMedianElapsedNs(result.medianElapsedNs());
            row.setMadElapsedNs(result.madElapsedNs());
            row.setMinElapsedNs(result.minElapsedNs());
            row.setMaxElapsedNs(result.maxElapsedNs());
            row.setOutputValidated(result.outputValidated());
            row.setOutcome(result.outcome());
            row.setEnvironmentFingerprint(poll.environmentFingerprint());
            benchmarkRunRepository.save(row);
        }
    }
}
