package com.hrishabh.algocracksubmissionservice.client;

import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.CasesRequest;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.CasesResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.ProfileMetadataResponse;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.dto.ReferenceSolutionDto;
import com.hrishabh.algocracksubmissionservice.dto.TestCaseDto;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.PotdResolveResponse;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.RankMetadataBatchRequest;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.RankMetadataBatchResponse;
import com.hrishabh.algocracksubmissionservice.helper.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * HTTP client for ProblemService APIs.
 * Replaces the deleted QuestionMetadataRepository, TestcaseRepository,
 * and ReferenceSolutionRepository.
 */
@Slf4j
@Component
public class ProblemServiceClient {

    private final RestTemplate restTemplate;
    private final String problemServiceUrl;
    private final String internalServiceToken;

    public ProblemServiceClient(
            RestTemplate restTemplate,
            @Value("${services.problem.base-url}") String problemServiceUrl,
            @Value("${services.problem.internal-service-token:}") String internalServiceToken) {
        this.restTemplate = restTemplate;
        this.problemServiceUrl = problemServiceUrl;
        this.internalServiceToken = internalServiceToken;
    }

    /**
     * Get testcases for a question, optionally filtered by type.
     * Replaces: TestcaseRepository.findByQuestionIdAndType() / findByQuestionId()
     */
    public List<TestCaseDto> getTestCases(Long questionId, String type) {
        String url = problemServiceUrl + "/api/v1/testcases/question/" + questionId;
        if (type != null) {
            url += "?type=" + type;
        }
        log.debug("Fetching testcases from: {}", url);
        return restTemplate.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<TestCaseDto>>() {
                }).getBody();
    }

    /**
     * Get question metadata for a specific language.
     * Replaces: QuestionMetadataRepository.findByQuestionIdAndLanguage()
     * and findByQuestionIdAndLanguageWithQuestion()
     */
    public QuestionMetadataApiDto getMetadata(Long questionId, String language) {
        String url = problemServiceUrl + "/api/v1/questions/" + questionId + "/metadata?language=" + language;
        log.debug("Fetching metadata from: {}", url);
        return restTemplate.getForObject(url, QuestionMetadataApiDto.class);
    }

    /**
     * Get reference solution (oracle) for a question.
     * Replaces: ReferenceSolutionRepository.findByQuestionId()
     */
    public ReferenceSolutionDto getOracle(Long questionId) {
        String url = problemServiceUrl + "/api/v1/internal/questions/" + questionId + "/reference-solution";
        log.debug("Fetching oracle from internal endpoint: {}", url);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(internalHeaders()), ReferenceSolutionDto.class)
                .getBody();
    }

    public Optional<ProfileMetadataResponse> getActiveComplexityProfile(long questionId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(problemServiceUrl
                        + "/api/v1/internal/questions/" + questionId + "/complexity-profile")
                .queryParam("language", language)
                .toUriString();
        try {
            ProfileMetadataResponse body = restTemplate.exchange(
                            url, HttpMethod.GET, new HttpEntity<>(internalHeaders()), ProfileMetadataResponse.class)
                    .getBody();
            return Optional.ofNullable(body);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        }
    }

    public CasesResponse generateComplexityProfileCases(long questionId, CasesRequest request) {
        String url = problemServiceUrl + "/api/v1/internal/questions/" + questionId + "/complexity-profile/cases";
        return restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<>(request, internalHeaders()),
                        CasesResponse.class)
                .getBody();
    }

    public RankMetadataBatchResponse fetchRankMetadataBatch(List<Long> questionIds) {
        String url = problemServiceUrl + "/api/v1/internal/questions/rank-metadata";
        log.debug("Fetching rank metadata batch from: {}", url);
        HttpEntity<RankMetadataBatchRequest> entity = new HttpEntity<>(
                new RankMetadataBatchRequest(questionIds),
                internalHeaders());
        return restTemplate.exchange(url, HttpMethod.POST, entity, RankMetadataBatchResponse.class).getBody();
    }

    public PotdResolveResponse resolvePotdCompletion(long questionId, Instant completedAt) {
        String url = UriComponentsBuilder.fromHttpUrl(problemServiceUrl + "/api/v1/internal/daily-challenges/resolve")
                .queryParam("questionId", questionId)
                .queryParam("completedAt", completedAt.toString())
                .toUriString();
        log.debug("Resolving POTD completion from: {}", url);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(internalHeaders()), PotdResolveResponse.class)
                .getBody();
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(CurrentUser.INTERNAL_CALL_HEADER, "true");
        if (internalServiceToken != null && !internalServiceToken.isBlank()) {
            headers.set("X-Internal-Service-Token", internalServiceToken);
        }
        return headers;
    }
}
