package com.hrishabh.algocracksubmissionservice.complexity.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocracksubmissionservice.complexity.dto.ComplexityProfileApiDtos.GeneratedCaseDto;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.PollResponse;
import com.hrishabh.algocracksubmissionservice.complexity.dto.CxeComplexityProfileDtos.ProfileCaseResult;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityBenchmarkRun;
import com.hrishabh.algocracksubmissionservice.complexity.repository.ComplexityBenchmarkRunRepository;
import com.hrishabh.algocracksubmissionservice.complexity.service.ComplexityAnalysisJsonSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexityBenchmarkRunPersisterTest {

    @Mock
    private ComplexityBenchmarkRunRepository repository;

    private ComplexityBenchmarkRunPersister persister;

    @BeforeEach
    void setUp() {
        persister = new ComplexityBenchmarkRunPersister(
                repository,
                new ComplexityAnalysisJsonSupport(new ObjectMapper()),
                new ComplexityProfileCorrelationValidator());
    }

    @Test
    void upsertPersistsInputHashAndCaseIdentity() {
        GeneratedCaseDto generated = new GeneratedCaseDto(
                "P", "v1", "hash", "gv1", "c1", "identity-1", Map.of("n", 8), "R", "s", "{}", null, "input-hash-1");
        PollResponse poll = new PollResponse(
                "exec",
                "COMPLETED",
                null,
                null,
                "hv1",
                "mpv1",
                null,
                "env",
                List.of(new ProfileCaseResult(
                        "c1", "identity-1", "P", "v1", "hash", "gv1", "R", Map.of("n", 8),
                        "SUCCESS", true, 1, 5, 100L, 5L, 90L, 110L, null)),
                null,
                null);
        when(repository.findByAnalysisIdAndCaseIdentity("analysis-1", "identity-1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        persister.upsertTerminalPoll("analysis-1", poll, List.of(generated), "hv1", "mpv1");

        ArgumentCaptor<ComplexityBenchmarkRun> captor = ArgumentCaptor.forClass(ComplexityBenchmarkRun.class);
        verify(repository).save(captor.capture());
        assertEquals("input-hash-1", captor.getValue().getInputHash());
        assertEquals("identity-1", captor.getValue().getCaseIdentity());
    }

    @Test
    void rejectsMismatchedCaseIdentity() {
        GeneratedCaseDto generated = new GeneratedCaseDto(
                "P", "v1", "hash", "gv1", "c1", "identity-1", Map.of("n", 8), "R", "s", "{}", null, "input-hash-1");
        PollResponse poll = new PollResponse(
                "exec",
                "COMPLETED",
                null,
                null,
                "hv1",
                "mpv1",
                null,
                "env",
                List.of(new ProfileCaseResult(
                        "wrong", "other", "P", "v1", "hash", "gv1", "R", Map.of("n", 8),
                        "SUCCESS", true, 1, 5, 100L, 5L, 90L, 110L, null)),
                null,
                null);
        assertThrows(
                ComplexityProfileCorrelationValidator.ProfileContractException.class,
                () -> persister.upsertTerminalPoll("analysis-1", poll, List.of(generated), "hv1", "mpv1"));
    }

    @Test
    void reprocessingSameResultUpdatesSameRow() {
        GeneratedCaseDto generated = new GeneratedCaseDto(
                "P", "v1", "hash", "gv1", "c1", "identity-1", Map.of("n", 8), "R", "s", "{}", null, "input-hash-1");
        PollResponse poll = new PollResponse(
                "exec",
                "COMPLETED",
                null,
                null,
                "hv1",
                "mpv1",
                null,
                "env",
                List.of(new ProfileCaseResult(
                        "c1", "identity-1", "P", "v1", "hash", "gv1", "R", Map.of("n", 8),
                        "SUCCESS", true, 1, 5, 100L, 5L, 90L, 110L, null)),
                null,
                null);
        ComplexityBenchmarkRun existing = ComplexityBenchmarkRun.builder()
                .analysisId("analysis-1")
                .caseIdentity("identity-1")
                .build();
        when(repository.findByAnalysisIdAndCaseIdentity("analysis-1", "identity-1"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        persister.upsertTerminalPoll("analysis-1", poll, List.of(generated), "hv1", "mpv1");
        persister.upsertTerminalPoll("analysis-1", poll, List.of(generated), "hv1", "mpv1");

        verify(repository, times(2)).save(existing);
    }
}
