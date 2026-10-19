package com.hrishabh.algocracksubmissionservice.statistics.controller;

import com.hrishabh.algocracksubmissionservice.helper.CurrentUser;
import com.hrishabh.algocracksubmissionservice.models.QuestionStatistics;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsReconciliationService;
import com.hrishabh.algocracksubmissionservice.statistics.dto.QuestionStatisticsReconcileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/question-statistics")
@RequiredArgsConstructor
public class QuestionStatisticsAdminController {

    private final QuestionStatisticsReconciliationService reconciliationService;

    @PostMapping("/{questionId}/reconcile")
    public ResponseEntity<QuestionStatisticsReconcileResponse> reconcile(
            @RequestHeader(value = CurrentUser.USER_ID_HEADER, required = false) String userIdHeader,
            @RequestHeader(value = CurrentUser.ROLE_HEADER, required = false) String roleHeader,
            @PathVariable Long questionId) {
        CurrentUser.requireAdmin(userIdHeader, roleHeader);
        QuestionStatistics stats = reconciliationService.reconcile(questionId);
        return ResponseEntity.ok(QuestionStatisticsReconcileResponse.builder()
                .questionId(questionId)
                .totalSubmissions(stats.getTotalSubmissions())
                .acceptedSubmissions(stats.getAcceptedSubmissions())
                .version(stats.getVersion())
                .build());
    }
}
