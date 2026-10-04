package com.hrishabh.algocracksubmissionservice.complexity.controller;

import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisDetailResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisHistoryResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisRequestResponse;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexityAnalysisReadService;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexityAnalysisRequestService;
import com.hrishabh.algocracksubmissionservice.helper.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/submissions/{submissionId}/complexity-analyses")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "complexity", name = "api-enabled", havingValue = "true")
public class ComplexityAnalysisController {

    private final ComplexityAnalysisRequestService requestService;
    private final ComplexityAnalysisReadService readService;

    @PostMapping
    public ResponseEntity<ComplexityAnalysisRequestResponse> requestAnalysis(
            @PathVariable("submissionId") String submissionId,
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader) {
        String userId = CurrentUser.require(userIdHeader);
        ComplexityAnalysisRequestResponse body = requestService.requestAnalysis(submissionId, userId);
        HttpStatus status = body.reused() ? HttpStatus.ACCEPTED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping
    public ComplexityAnalysisHistoryResponse listAnalyses(
            @PathVariable("submissionId") String submissionId,
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = CurrentUser.require(userIdHeader);
        return readService.listAnalyses(submissionId, userId, page, size);
    }

    @GetMapping("/{analysisId}")
    public ComplexityAnalysisDetailResponse getAnalysis(
            @PathVariable("submissionId") String submissionId,
            @PathVariable String analysisId,
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader) {
        String userId = CurrentUser.require(userIdHeader);
        return readService.getAnalysis(submissionId, analysisId, userId);
    }
}
