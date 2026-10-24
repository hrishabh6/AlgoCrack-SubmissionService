package com.hrishabh.algocracksubmissionservice.playground;

import com.hrishabh.algocracksubmissionservice.dto.ExecutionMode;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionRequest;
import com.hrishabh.algocracksubmissionservice.dto.ExecutionResponse;
import com.hrishabh.algocracksubmissionservice.dto.SubmissionStatusDto;
import com.hrishabh.algocracksubmissionservice.exception.ValidationException;
import com.hrishabh.algocracksubmissionservice.playground.config.PlaygroundProperties;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.PlaygroundRunRequest;
import com.hrishabh.algocracksubmissionservice.playground.dto.PlaygroundDtos.PlaygroundRunResponse;
import com.hrishabh.algocracksubmissionservice.playground.metrics.PlaygroundMetrics;
import com.hrishabh.algocracksubmissionservice.playground.service.PlaygroundRunGuardService;
import com.hrishabh.algocracksubmissionservice.playground.service.PlaygroundRunService;
import com.hrishabh.algocracksubmissionservice.playground.service.PlaygroundWorkspaceService;
import com.hrishabh.algocracksubmissionservice.service.CodeExecutionClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaygroundRunServiceTest {

    @Mock
    private PlaygroundWorkspaceService workspaceService;
    @Mock
    private PlaygroundRunGuardService runGuardService;
    @Mock
    private CodeExecutionClientService codeExecutionClientService;
    @Mock
    private PlaygroundMetrics playgroundMetrics;

    private PlaygroundRunService service;

    @BeforeEach
    void setUp() {
        PlaygroundProperties properties = new PlaygroundProperties();
        properties.setRunEnabled(true);
        service = new PlaygroundRunService(
                properties,
                workspaceService,
                runGuardService,
                codeExecutionClientService,
                playgroundMetrics);
    }

    @Test
    void rejectsPythonBeforeAnyExecutionCall() {
        when(workspaceService.normalizeRunLanguage("PYTHON")).thenReturn("PYTHON");

        ValidationException ex = assertThrows(ValidationException.class, () -> service.run(
                "user-1",
                new PlaygroundRunRequest("PYTHON", "print(1)", "")));

        assertEquals("Playground execution supports Java only", ex.getMessage());
        verify(playgroundMetrics).recordRunRejection("unsupported_language");
        verifyNoInteractions(codeExecutionClientService, runGuardService);
    }

    @Test
    void javaRunKeepsTheExistingResponseContract() {
        when(workspaceService.normalizeRunLanguage("JAVA")).thenReturn("JAVA");
        when(workspaceService.validateRunSource("class Main {}")).thenReturn("class Main {}");
        when(workspaceService.validateRunStdin("")).thenReturn("");
        when(codeExecutionClientService.submitCode(any())).thenReturn(
                ExecutionResponse.builder().submissionId("playground-1").build());
        SubmissionStatusDto completed = SubmissionStatusDto.builder()
                .status("COMPLETED")
                .rawExecutionStatus("SUCCESS")
                .stdout("4")
                .stderr("")
                .compilationOutput("")
                .runtimeMs(12)
                .memoryKb(1)
                .exitCode(0)
                .outputTruncated(false)
                .build();
        when(codeExecutionClientService.getStatus("playground-1")).thenReturn(completed);
        when(codeExecutionClientService.getResults("playground-1")).thenReturn(completed);

        PlaygroundRunResponse response = service.run(
                "user-1",
                new PlaygroundRunRequest("JAVA", "class Main {}", ""));

        assertEquals("SUCCESS", response.status());
        assertEquals("4", response.stdout());
        assertEquals(0, response.exitCode());
        ArgumentCaptor<ExecutionRequest> captor = ArgumentCaptor.forClass(ExecutionRequest.class);
        verify(codeExecutionClientService).submitCode(captor.capture());
        assertEquals("JAVA", captor.getValue().getLanguage());
        assertEquals(ExecutionMode.PLAYGROUND.name(), captor.getValue().getExecutionMode());
        verify(runGuardService).acquire("user-1");
        verify(runGuardService).release("user-1");
        verify(playgroundMetrics, never()).recordRunRejection("unsupported_language");
    }
}
