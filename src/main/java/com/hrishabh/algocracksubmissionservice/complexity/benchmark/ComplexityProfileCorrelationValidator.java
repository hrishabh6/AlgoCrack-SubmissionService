package com.hrishabh.algocracksubmissionservice.complexity.benchmark;

import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.CasesResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.GeneratedCaseDto;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.ProfileMetadataResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.PollResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.ProfileCaseResult;
import org.springframework.stereotype.Component;

@Component
public class ComplexityProfileCorrelationValidator {

    public void validateProfileMetadata(ProfileMetadataResponse profile, long questionId, String language) {
        if (profile.questionId() != questionId) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "question mismatch");
        }
        if (!language.equalsIgnoreCase(profile.language())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "language mismatch");
        }
    }

    public void validateCasesResponse(CasesResponse cases, ProfileMetadataResponse profile) {
        if (!profile.profileVersion().equals(cases.profileVersion())
                || !profile.profileHash().equals(cases.profileHash())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "cases profile version/hash mismatch");
        }
        if (!profile.generatorVersion().equals(cases.generatorVersion())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "generator version mismatch");
        }
    }

    public void validateGeneratedCase(GeneratedCaseDto caseDto, ProfileMetadataResponse profile) {
        if (!profile.profileVersion().equals(caseDto.profileVersion())
                || !profile.profileHash().equals(caseDto.profileHash())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "case profile drift");
        }
        if (!profile.generatorVersion().equals(caseDto.generatorVersion())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "case generator drift");
        }
    }

    public void validatePollResponse(PollResponse poll, ProfileMetadataResponse profile, String harnessVersion) {
        if (poll.harnessVersion() != null && !harnessVersion.equals(poll.harnessVersion())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "harness version mismatch");
        }
        if (poll.cases() == null) {
            return;
        }
        for (ProfileCaseResult result : poll.cases()) {
            if (!profile.profileVersion().equals(result.profileVersion())
                    || !profile.profileHash().equals(result.profileHash())) {
                throw new ProfileContractException("PROFILE_HASH_DRIFT", "poll case profile drift");
            }
            if (!profile.generatorVersion().equals(result.generatorVersion())) {
                throw new ProfileContractException("PROFILE_HASH_DRIFT", "poll generator drift");
            }
        }
    }

    public void validatePollCaseMatchesGenerated(GeneratedCaseDto expected, ProfileCaseResult result) {
        if (!expected.caseId().equals(result.caseId()) || !expected.caseIdentity().equals(result.caseIdentity())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "case identity mismatch");
        }
        if (!expected.profileVersion().equals(result.profileVersion())
                || !expected.profileHash().equals(result.profileHash())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "poll case profile drift");
        }
        if (!expected.generatorVersion().equals(result.generatorVersion())) {
            throw new ProfileContractException("PROFILE_HASH_DRIFT", "poll generator drift");
        }
    }

    public static class ProfileContractException extends RuntimeException {
        private final String errorCode;

        public ProfileContractException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
