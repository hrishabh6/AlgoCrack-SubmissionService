package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.PotdResolveResponse;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.QuestionRankMetadataItem;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.RankMetadataBatchResponse;
import com.hrishabh.algocracksubmissionservice.dto.progress.QuestionRankMetadataApiDtos.TagRef;
import com.hrishabh.algocracksubmissionservice.models.Submission;
import com.hrishabh.algocracksubmissionservice.progress.model.DailyChallengeCompletion;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProblemSolve;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import com.hrishabh.algocracksubmissionservice.progress.model.UserTopicProgress;
import com.hrishabh.algocracksubmissionservice.progress.rank.NormalizedDifficulty;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankCalculationInput;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankCalculationResult;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankCalculator;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankConstants;
import com.hrishabh.algocracksubmissionservice.progress.rank.TopicBreadthCalculator;
import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;
import com.hrishabh.algocracksubmissionservice.progress.repository.DailyChallengeCompletionRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserProblemSolveRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserProgressRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserTopicProgressRepository;
import com.hrishabh.algocracksubmissionservice.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ProgressRecalculationService {

    private static final int METADATA_BATCH = 200;

    private final SubmissionRepository submissionRepository;
    private final ProblemServiceClient problemServiceClient;
    private final UserProblemSolveRepository userProblemSolveRepository;
    private final DailyChallengeCompletionRepository dailyChallengeCompletionRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserTopicProgressRepository userTopicProgressRepository;
    private final RankCalculator rankCalculator;
    private final RankProperties rankProperties;
    private final Clock clock;

    public ProgressRecalculationService(
            SubmissionRepository submissionRepository,
            ProblemServiceClient problemServiceClient,
            UserProblemSolveRepository userProblemSolveRepository,
            DailyChallengeCompletionRepository dailyChallengeCompletionRepository,
            UserProgressRepository userProgressRepository,
            UserTopicProgressRepository userTopicProgressRepository,
            RankCalculator rankCalculator,
            RankProperties rankProperties,
            Clock clock) {
        this.submissionRepository = submissionRepository;
        this.problemServiceClient = problemServiceClient;
        this.userProblemSolveRepository = userProblemSolveRepository;
        this.dailyChallengeCompletionRepository = dailyChallengeCompletionRepository;
        this.userProgressRepository = userProgressRepository;
        this.userTopicProgressRepository = userTopicProgressRepository;
        this.rankCalculator = rankCalculator;
        this.rankProperties = rankProperties;
        this.clock = clock;
    }

    @Transactional
    public UserProgress recalculateUser(String userId) {
        List<Submission> accepted = submissionRepository.findAcceptedByUserIdOrderByCompletedAt(userId);
        clearDerivedState(userId);

        if (accepted.isEmpty()) {
            return persistEmptyProgress(userId);
        }

        Map<Long, Submission> firstAcceptByQuestion = new LinkedHashMap<>();
        for (Submission submission : accepted) {
            firstAcceptByQuestion.putIfAbsent(submission.getQuestionId(), submission);
        }

        Map<Long, QuestionRankMetadataItem> metadataByQuestion =
                loadMetadata(firstAcceptByQuestion.keySet());

        List<UserProblemSolve> solves = new ArrayList<>();
        for (Map.Entry<Long, Submission> entry : firstAcceptByQuestion.entrySet()) {
            solves.add(toUserProblemSolve(userId, entry.getValue(), metadataByQuestion.get(entry.getKey())));
        }
        userProblemSolveRepository.saveAll(solves);

        Set<Long> completedChallengeIds = new HashSet<>();
        List<DailyChallengeCompletion> potdRows = new ArrayList<>();
        for (Submission submission : accepted) {
            Instant completedInstant = submissionCompletedInstant(submission);
            PotdResolveResponse potd = problemServiceClient.resolvePotdCompletion(
                    submission.getQuestionId(), completedInstant);
            if (potd == null || !potd.isQualifies() || potd.getChallengeId() == null) {
                continue;
            }
            if (!completedChallengeIds.add(potd.getChallengeId())) {
                continue;
            }
            potdRows.add(DailyChallengeCompletion.builder()
                    .userId(userId)
                    .dailyChallengeId(potd.getChallengeId())
                    .challengeDate(LocalDate.parse(potd.getChallengeDate()))
                    .questionId(submission.getQuestionId())
                    .acceptedSubmissionId(submission.getSubmissionId())
                    .difficultySnapshot(potd.getNormalizedDifficulty())
                    .completedAt(submission.getCompletedAt() != null
                            ? submission.getCompletedAt()
                            : submission.getQueuedAt())
                    .build());
        }
        dailyChallengeCompletionRepository.saveAll(potdRows);

        long easy = 0;
        long medium = 0;
        long hard = 0;
        List<Map<Long, BigDecimal>> perSolveCredits = new ArrayList<>();
        Map<Long, String> tagNames = new HashMap<>();
        Map<Long, Integer> tagContributions = new HashMap<>();

        for (UserProblemSolve solve : solves) {
            QuestionRankMetadataItem meta = metadataByQuestion.get(solve.getQuestionId());
            if (!countsTowardMastery(meta)) {
                continue;
            }
            NormalizedDifficulty difficulty = NormalizedDifficulty.fromRaw(solve.getDifficultySnapshot());
            switch (difficulty) {
                case EASY -> easy++;
                case MEDIUM -> medium++;
                case HARD -> hard++;
                default -> {
                }
            }
            if (meta.getTags() == null || meta.getTags().isEmpty()) {
                continue;
            }
            List<Long> tagIds = meta.getTags().stream().map(TagRef::getTagId).toList();
            for (TagRef tag : meta.getTags()) {
                tagNames.putIfAbsent(tag.getTagId(), tag.getTagName());
            }
            Map<Long, BigDecimal> credit = TopicBreadthCalculator.creditForSolve(tagIds);
            perSolveCredits.add(credit);
            for (Long tagId : credit.keySet()) {
                tagContributions.merge(tagId, 1, Integer::sum);
            }
        }

        long easyPotd = 0;
        long mediumPotd = 0;
        long hardPotd = 0;
        List<LocalDate> potdDates = new ArrayList<>();
        for (DailyChallengeCompletion completion : potdRows) {
            potdDates.add(completion.getChallengeDate());
            switch (NormalizedDifficulty.fromRaw(completion.getDifficultySnapshot())) {
                case EASY -> easyPotd++;
                case MEDIUM -> mediumPotd++;
                case HARD -> hardPotd++;
                default -> {
                }
            }
        }
        potdDates.sort(LocalDate::compareTo);

        Map<Long, BigDecimal> topicCredits = TopicBreadthCalculator.mergeCredits(perSolveCredits);
        RankCalculationInput input = RankCalculationInput.builder()
                .easySolved(easy)
                .mediumSolved(medium)
                .hardSolved(hard)
                .easyPotdCompleted(easyPotd)
                .mediumPotdCompleted(mediumPotd)
                .hardPotdCompleted(hardPotd)
                .topicCredits(topicCredits)
                .potdCompletionDatesUtc(potdDates)
                .build();

        LocalDate todayUtc = LocalDate.now(clock);
        RankCalculationResult calculated = rankCalculator.calculate(input, todayUtc);

        List<UserTopicProgress> topicRows = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : topicCredits.entrySet()) {
            long tagId = entry.getKey();
            BigDecimal credit = entry.getValue();
            topicRows.add(UserTopicProgress.builder()
                    .userId(userId)
                    .tagId(tagId)
                    .tagNameSnapshot(tagNames.get(tagId))
                    .normalizedCredit(credit)
                    .contributingSolves(tagContributions.getOrDefault(tagId, 0))
                    .topicLevel(TopicLevelResolver.resolve(credit, rankProperties.getBreadth()))
                    .rankAlgorithmVersion(RankConstants.ALGORITHM_VERSION)
                    .build());
        }
        userTopicProgressRepository.saveAll(topicRows);

        LocalDateTime calculatedAt = LocalDateTime.now(clock);
        LocalDateTime scoreAchievedAt = accepted.stream()
                .map(s -> s.getCompletedAt() != null ? s.getCompletedAt() : s.getQueuedAt())
                .max(LocalDateTime::compareTo)
                .orElse(null);

        UserProgress progress = UserProgress.builder()
                .userId(userId)
                .rankAlgorithmVersion(RankConstants.ALGORITHM_VERSION)
                .uniqueSolved(easy + medium + hard)
                .easySolved(easy)
                .mediumSolved(medium)
                .hardSolved(hard)
                .easyPotdCompleted(easyPotd)
                .mediumPotdCompleted(mediumPotd)
                .hardPotdCompleted(hardPotd)
                .totalPotdCompleted(potdRows.size())
                .currentPotdStreak(calculated.getCurrentPotdStreak())
                .longestPotdStreak(calculated.getLongestPotdStreak())
                .breadthQualifiedTopics(calculated.getBreadthQualifiedTopics())
                .masteryScore(calculated.getMasteryScore())
                .potdScore(calculated.getPotdScore())
                .breadthScore(calculated.getBreadthScore())
                .qualityScore(calculated.getQualityScore())
                .contestScore(calculated.getContestScore())
                .totalRankScore(calculated.getTotalRankScore())
                .rankTierCode(calculated.getRankTierCode())
                .scoreAchievedAt(scoreAchievedAt)
                .calculatedAt(calculatedAt)
                .build();

        return userProgressRepository.save(progress);
    }

    private void clearDerivedState(String userId) {
        userProblemSolveRepository.deleteByUserId(userId);
        dailyChallengeCompletionRepository.deleteByUserId(userId);
        userTopicProgressRepository.deleteByUserIdAndRankAlgorithmVersion(userId, RankConstants.ALGORITHM_VERSION);
        userProgressRepository.deleteByUserIdAndRankAlgorithmVersion(userId, RankConstants.ALGORITHM_VERSION);
    }

    private UserProgress persistEmptyProgress(String userId) {
        LocalDate todayUtc = LocalDate.now(clock);
        RankCalculationResult calculated = rankCalculator.calculate(
                RankCalculationInput.builder()
                        .easySolved(0)
                        .mediumSolved(0)
                        .hardSolved(0)
                        .easyPotdCompleted(0)
                        .mediumPotdCompleted(0)
                        .hardPotdCompleted(0)
                        .topicCredits(Map.of())
                        .potdCompletionDatesUtc(List.of())
                        .build(),
                todayUtc);
        UserProgress progress = UserProgress.builder()
                .userId(userId)
                .rankAlgorithmVersion(RankConstants.ALGORITHM_VERSION)
                .currentPotdStreak(calculated.getCurrentPotdStreak())
                .longestPotdStreak(calculated.getLongestPotdStreak())
                .rankTierCode(calculated.getRankTierCode())
                .calculatedAt(LocalDateTime.now(clock))
                .build();
        return userProgressRepository.save(progress);
    }

    private Map<Long, QuestionRankMetadataItem> loadMetadata(Set<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        List<Long> idList = new ArrayList<>(questionIds);
        Map<Long, QuestionRankMetadataItem> merged = new HashMap<>();
        for (int i = 0; i < idList.size(); i += METADATA_BATCH) {
            List<Long> chunk = idList.subList(i, Math.min(i + METADATA_BATCH, idList.size()));
            RankMetadataBatchResponse response = problemServiceClient.fetchRankMetadataBatch(chunk);
            if (response == null || response.getItems() == null) {
                continue;
            }
            for (QuestionRankMetadataItem item : response.getItems()) {
                merged.put(item.getQuestionId(), item);
            }
        }
        return merged;
    }

    private static UserProblemSolve toUserProblemSolve(
            String userId,
            Submission submission,
            QuestionRankMetadataItem meta) {
        String difficulty = meta != null ? meta.getNormalizedDifficulty() : "UNKNOWN";
        String status = meta != null ? meta.getQuestionStatus() : "NOT_FOUND";
        LocalDateTime acceptedAt = submission.getCompletedAt() != null
                ? submission.getCompletedAt()
                : submission.getQueuedAt();
        return UserProblemSolve.builder()
                .userId(userId)
                .questionId(submission.getQuestionId())
                .firstAcceptedSubmissionId(submission.getSubmissionId())
                .firstAcceptedAt(acceptedAt)
                .difficultySnapshot(difficulty)
                .questionStatusSnapshot(status)
                .build();
    }

    private static boolean countsTowardMastery(QuestionRankMetadataItem meta) {
        if (meta == null) {
            return false;
        }
        if (!"PUBLISHED".equalsIgnoreCase(meta.getQuestionStatus())) {
            return false;
        }
        return NormalizedDifficulty.fromRaw(meta.getNormalizedDifficulty()) != NormalizedDifficulty.UNKNOWN;
    }

    private static Instant submissionCompletedInstant(Submission submission) {
        LocalDateTime at = submission.getCompletedAt() != null
                ? submission.getCompletedAt()
                : submission.getQueuedAt();
        return at.atZone(ZoneOffset.UTC).toInstant();
    }
}
