package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.PotdResolveResponse;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionStatus;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.progress.dto.PotdSubmissionOutcomeDto;
import com.hrishabh.algocracksubmissionservice.progress.model.DailyChallengeCompletion;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import com.hrishabh.algocracksubmissionservice.progress.repository.DailyChallengeCompletionRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PotdCompletionServiceTest {

    @Mock
    private ProblemServiceClient problemServiceClient;
    @Mock
    private DailyChallengeCompletionRepository dailyChallengeCompletionRepository;
    @Mock
    private UserProgressRepository userProgressRepository;

    private PotdCompletionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneOffset.UTC);
        service = new PotdCompletionService(
                problemServiceClient, dailyChallengeCompletionRepository, userProgressRepository, clock);
    }

    @Test
    void firstQualifyingAcceptanceIsNewlyCompleted() {
        Submission submission = acceptedSubmission("sub-1", 12L);
        when(problemServiceClient.resolvePotdCompletion(eq(12L), any()))
                .thenReturn(PotdResolveResponse.builder()
                        .qualifies(true)
                        .challengeId(99L)
                        .challengeDate("2026-10-17")
                        .normalizedDifficulty("MEDIUM")
                        .build());
        when(dailyChallengeCompletionRepository.existsByUserIdAndDailyChallengeId("user-1", 99L))
                .thenReturn(false);
        when(dailyChallengeCompletionRepository.findByUserId("user-1"))
                .thenReturn(List.of(DailyChallengeCompletion.builder()
                        .challengeDate(LocalDate.of(2026, 10, 17))
                        .build()));
        when(userProgressRepository.findByUserIdAndRankAlgorithmVersion(any(), any()))
                .thenReturn(Optional.of(UserProgress.builder().longestPotdStreak(2).build()));

        Optional<PotdSubmissionOutcomeDto> outcome = service.recordAcceptedSubmission(submission);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().isNewlyCompleted()).isTrue();
        assertThat(outcome.get().getCurrentPotdStreak()).isEqualTo(1);
        verify(dailyChallengeCompletionRepository).save(any(DailyChallengeCompletion.class));
    }

    @Test
    void repeatQualifyingAcceptanceIsNotNewlyCompleted() {
        Submission submission = acceptedSubmission("sub-2", 12L);
        when(problemServiceClient.resolvePotdCompletion(eq(12L), any()))
                .thenReturn(PotdResolveResponse.builder()
                        .qualifies(true)
                        .challengeId(99L)
                        .challengeDate("2026-10-17")
                        .build());
        when(dailyChallengeCompletionRepository.existsByUserIdAndDailyChallengeId("user-1", 99L))
                .thenReturn(true);
        when(userProgressRepository.findByUserIdAndRankAlgorithmVersion(any(), any()))
                .thenReturn(Optional.empty());

        Optional<PotdSubmissionOutcomeDto> outcome = service.recordAcceptedSubmission(submission);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().isNewlyCompleted()).isFalse();
        verify(dailyChallengeCompletionRepository, never()).save(any());
    }

    @Test
    void outcomeForSubmissionMatchesCreditedRow() {
        Submission submission = acceptedSubmission("sub-1", 12L);
        when(dailyChallengeCompletionRepository.findByAcceptedSubmissionId("sub-1"))
                .thenReturn(Optional.of(DailyChallengeCompletion.builder()
                        .challengeDate(LocalDate.of(2026, 10, 17))
                        .build()));
        when(userProgressRepository.findByUserIdAndRankAlgorithmVersion(any(), any()))
                .thenReturn(Optional.of(UserProgress.builder().currentPotdStreak(3).build()));

        Optional<PotdSubmissionOutcomeDto> outcome = service.outcomeForSubmission(submission);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().isNewlyCompleted()).isTrue();
        assertThat(outcome.get().getCurrentPotdStreak()).isEqualTo(3);
    }

    private static Submission acceptedSubmission(String id, long questionId) {
        return Submission.builder()
                .submissionId(id)
                .userId("user-1")
                .questionId(questionId)
                .status(SubmissionStatus.COMPLETED)
                .verdict(SubmissionVerdict.ACCEPTED)
                .completedAt(LocalDateTime.of(2026, 10, 17, 10, 0))
                .queuedAt(LocalDateTime.of(2026, 10, 17, 9, 59))
                .build();
    }
}
