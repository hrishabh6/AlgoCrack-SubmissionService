package com.hrishabh.algocracksubmissionservice.client;

import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import com.hrishabh.algocracksubmissionservice.dto.ReferenceSolutionDto;
import com.hrishabh.algocracksubmissionservice.dto.TestCaseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

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

    public ProblemServiceClient(
            RestTemplate restTemplate,
            @Value("${services.problem.base-url}") String problemServiceUrl) {
        this.restTemplate = restTemplate;
        this.problemServiceUrl = problemServiceUrl;
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
        String url = problemServiceUrl + "/api/v1/questions/" + questionId + "/reference-solution";
        log.debug("Fetching oracle from: {}", url);
        return restTemplate.getForObject(url, ReferenceSolutionDto.class);
    }
}
