package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.PotdResolveResponse;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.models.SubmissionVerdict;
import com.hrishabh.algocracksubmissionservice.progress.dto.PotdSubmissionOutcomeDto;
import com.hrishabh.algocracksubmissionservice.progress.model.DailyChallengeCompletion;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import com.hrishabh.algocracksubmissionservice.progress.rank.PotdStreakCalculator;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankConstants;
import com.hrishabh.algocracksubmissionservice.progress.repository.DailyChallengeCompletionRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PotdCompletionService {

    private final ProblemServiceClient problemServiceClient;
    private final DailyChallengeCompletionRepository dailyChallengeCompletionRepository;
    private final UserProgressRepository userProgressRepository;
    private final Clock clock;

    /**
     * Records the first qualifying accepted submission for a published daily challenge.
     * Idempotent per user/challenge via {@code uk_dcc_user_challenge}.
     * Requires a new transaction because completion is dispatched after the verdict commits,
     * while Spring still has the finished transaction's synchronization active.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PotdSubmissionOutcomeDto> recordAcceptedSubmission(Submission submission) {
        if (submission.getVerdict() != SubmissionVerdict.ACCEPTED) {
            return Optional.empty();
        }
        Instant completedInstant = submissionCompletedInstant(submission);
        PotdResolveResponse potd = problemServiceClient.resolvePotdCompletion(
                submission.getQuestionId(), completedInstant);
        if (potd == null || !potd.isQualifies() || potd.getChallengeId() == null) {
            return Optional.empty();
        }

        long challengeId = potd.getChallengeId();
        String userId = submission.getUserId();
        if (dailyChallengeCompletionRepository.existsByUserIdAndDailyChallengeId(userId, challengeId)) {
            return Optional.of(PotdSubmissionOutcomeDto.builder()
                    .dailyChallengeCompleted(true)
                    .newlyCompleted(false)
                    .challengeDate(potd.getChallengeDate())
                    .currentPotdStreak(readCurrentStreak(userId))
                    .build());
        }

        dailyChallengeCompletionRepository.save(DailyChallengeCompletion.builder()
                .userId(userId)
                .dailyChallengeId(challengeId)
                .challengeDate(LocalDate.parse(potd.getChallengeDate()))
                .questionId(submission.getQuestionId())
                .acceptedSubmissionId(submission.getSubmissionId())
                .difficultySnapshot(potd.getNormalizedDifficulty())
                .completedAt(submission.getCompletedAt() != null
                        ? submission.getCompletedAt()
                        : submission.getQueuedAt())
                .build());

        int streak = refreshPotdStreak(userId);
        log.info("Recorded POTD completion user={} challenge={} submission={}",
                userId, challengeId, submission.getSubmissionId());

        return Optional.of(PotdSubmissionOutcomeDto.builder()
                .dailyChallengeCompleted(true)
                .newlyCompleted(true)
                .challengeDate(potd.getChallengeDate())
                .currentPotdStreak(streak)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<PotdSubmissionOutcomeDto> outcomeForSubmission(Submission submission) {
        if (submission.getVerdict() != SubmissionVerdict.ACCEPTED) {
            return Optional.empty();
        }
        return dailyChallengeCompletionRepository.findByAcceptedSubmissionId(submission.getSubmissionId())
                .map(completion -> PotdSubmissionOutcomeDto.builder()
                        .dailyChallengeCompleted(true)
                        .newlyCompleted(true)
                        .challengeDate(completion.getChallengeDate().toString())
                        .currentPotdStreak(readCurrentStreak(submission.getUserId()))
                        .build());
    }

    private int refreshPotdStreak(String userId) {
        List<LocalDate> dates = new ArrayList<>();
        for (DailyChallengeCompletion row : dailyChallengeCompletionRepository.findByUserId(userId)) {
            dates.add(row.getChallengeDate());
        }
        LocalDate todayUtc = LocalDate.now(clock);
        PotdStreakCalculator.StreakResult streak = PotdStreakCalculator.calculate(dates, todayUtc);

        userProgressRepository
                .findByUserIdAndRankAlgorithmVersion(userId, RankConstants.ALGORITHM_VERSION)
                .ifPresent(progress -> {
                    progress.setCurrentPotdStreak(streak.currentStreak());
                    progress.setLongestPotdStreak(
                            Math.max(progress.getLongestPotdStreak(), streak.longestStreak()));
                    progress.setCalculatedAt(LocalDateTime.now(clock));
                    userProgressRepository.save(progress);
                });
        return streak.currentStreak();
    }

    private int readCurrentStreak(String userId) {
        return userProgressRepository
                .findByUserIdAndRankAlgorithmVersion(userId, RankConstants.ALGORITHM_VERSION)
                .map(UserProgress::getCurrentPotdStreak)
                .orElse(0);
    }

    private static Instant submissionCompletedInstant(Submission submission) {
        LocalDateTime completed = submission.getCompletedAt() != null
                ? submission.getCompletedAt()
                : submission.getQueuedAt();
        return completed.atZone(ZoneOffset.UTC).toInstant();
    }
}
