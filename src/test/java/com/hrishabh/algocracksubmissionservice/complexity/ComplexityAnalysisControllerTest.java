package com.hrishabh.algocracksubmissionservice.complexity;

import com.hrishabh.algocracksubmissionservice.complexity.controller.ComplexityAnalysisController;
import com.hrishabh.algocracksubmissionservice.complexity.controller.ComplexityExceptionHandler;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityAnalysisDtos.ComplexityAnalysisRequestResponse;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityForbiddenException;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityLanguageUnsupportedException;
import com.hrishabh.algocracksubmissionservice.complexity.exception.ComplexityNotAcceptedException;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexityAnalysisReadService;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexityAnalysisRequestService;
import com.hrishabh.algocracksubmissionservice.helper.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ComplexityAnalysisControllerTest {

    @Mock
    private ComplexityAnalysisRequestService requestService;

    @Mock
    private ComplexityAnalysisReadService readService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ComplexityAnalysisController controller = new ComplexityAnalysisController(requestService, readService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ComplexityExceptionHandler())
                .build();
    }

    @Test
    void postReturnsAcceptedRequestContract() throws Exception {
        when(requestService.requestAnalysis(eq("sub-1"), eq("user-1")))
                .thenReturn(new ComplexityAnalysisRequestResponse("a-1", "sub-1", "QUEUED", false, 1000L));

        mockMvc.perform(post("/api/v1/submissions/sub-1/complexity-analyses")
                        .header(CurrentUser.USER_ID_HEADER, "user-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value("a-1"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.reused").value(false))
                .andExpect(jsonPath("$.pollAfterMs").value(1000));
    }

    @Test
    void mapsNotAcceptedTo409() throws Exception {
        when(requestService.requestAnalysis(eq("sub-1"), eq("user-1")))
                .thenThrow(new ComplexityNotAcceptedException("Submission is not accepted"));

        mockMvc.perform(post("/api/v1/submissions/sub-1/complexity-analyses")
                        .header(CurrentUser.USER_ID_HEADER, "user-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_ACCEPTED"));
    }

    @Test
    void mapsForbiddenTo403() throws Exception {
        when(requestService.requestAnalysis(eq("sub-1"), eq("user-1")))
                .thenThrow(new ComplexityForbiddenException());

        mockMvc.perform(post("/api/v1/submissions/sub-1/complexity-analyses")
                        .header(CurrentUser.USER_ID_HEADER, "user-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ANALYSIS_FORBIDDEN"));
    }

    @Test
    void mapsUnsupportedLanguageTo422() throws Exception {
        when(requestService.requestAnalysis(eq("sub-1"), eq("user-1")))
                .thenThrow(new ComplexityLanguageUnsupportedException("Only Java is supported in V1"));

        mockMvc.perform(post("/api/v1/submissions/sub-1/complexity-analyses")
                        .header(CurrentUser.USER_ID_HEADER, "user-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LANGUAGE_UNSUPPORTED"));
    }
}
