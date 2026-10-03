package com.hrishabh.algocracksubmissionservice.progress.service;

import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeAwardCandidate;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEngine;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluationContext;
import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;
import com.hrishabh.algocracksubmissionservice.progress.dto.ProgressApiDtos.*;
import com.hrishabh.algocracksubmissionservice.progress.model.BadgeDefinition;
import com.hrishabh.algocracksubmissionservice.progress.model.UserBadge;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import com.hrishabh.algocracksubmissionservice.progress.model.UserTopicProgress;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankConstants;
import com.hrishabh.algocracksubmissionservice.progress.rank.RankTierResolver;
import com.hrishabh.algocracksubmissionservice.progress.repository.BadgeDefinitionRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserBadgeRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserProgressRepository;
import com.hrishabh.algocracksubmissionservice.progress.repository.UserTopicProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressReadService {

    private final UserProgressRepository userProgressRepository;
    private final UserTopicProgressRepository userTopicProgressRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BadgeDefinitionRepository badgeDefinitionRepository;
    private final BadgeEngine badgeEngine;
    private final RankProperties rankProperties;

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(String userId, boolean includeLeaderboardPosition) {
        UserProgress progress = userProgressRepository
                .findByUserIdAndRankAlgorithmVersion(userId, RankConstants.ALGORITHM_VERSION)
                .orElse(null);
        if (progress == null) {
            return emptyProgress(userId, includeLeaderboardPosition);
        }
        Long position = includeLeaderboardPosition ? resolveLeaderboardPosition(progress) : null;
        return toProgressResponse(progress, position);
    }

    @Transactional(readOnly = true)
    public LeaderboardPositionResponse getLeaderboardPosition(String userId) {
        UserProgress progress = userProgressRepository
                .findByUserIdAndRankAlgorithmVersion(userId, RankConstants.ALGORITHM_VERSION)
                .orElse(null);
        if (progress == null || progress.getTotalRankScore() <= 0) {
            return LeaderboardPositionResponse.builder()
                    .userId(userId)
                    .position(null)
                    .totalScore(0)
                    .tierCode(RankTierResolver.resolveTierCode(0, rankProperties.getTiers()))
                    .build();
        }
        return LeaderboardPositionResponse.builder()
                .userId(userId)
                .position(resolveLeaderboardPosition(progress))
                .totalScore(progress.getTotalRankScore())
                .tierCode(progress.getRankTierCode())
                .build();
    }

    @Transactional(readOnly = true)
    public UserBadgesResponse getUserBadges(String userId) {
        List<BadgeDefinition> definitions = badgeDefinitionRepository.findByActiveTrueOrderBySortOrderAsc();
        Map<String, BadgeDefinition> definitionByCode = definitions.stream()
                .collect(Collectors.toMap(BadgeDefinition::getCode, d -> d, (a, b) -> a));

        List<UserBadge> earnedRows = userBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId);
        Set<String> earnedCodes = earnedRows.stream().map(UserBadge::getBadgeCode).collect(Collectors.toSet());

        List<EarnedBadgeDto> earned = new ArrayList<>();
        for (UserBadge row : earnedRows) {
            BadgeDefinition def = definitionByCode.get(row.getBadgeCode());
            if (def == null) {
                continue;
            }
            earned.add(EarnedBadgeDto.builder()
                    .code(def.getCode())
                    .name(def.getName())
                    .description(def.getDescription())
                    .category(def.getCategory())
                    .iconKey(def.getIconKey())
                    .earnedAt(row.getEarnedAt())
                    .build());
        }

        UserProgress progress = userProgressRepository
                .findByUserIdAndRankAlgorithmVersion(userId, RankConstants.ALGORITHM_VERSION)
                .orElse(null);
        List<UserTopicProgress> topics = userTopicProgressRepository.findByUserIdAndRankAlgorithmVersion(
                userId, RankConstants.ALGORITHM_VERSION);

        BadgeEvaluationContext context = BadgeEvaluationContext.builder()
                .userId(userId)
                .progress(progress != null ? progress : emptyUserProgress(userId))
                .topicProgress(topics)
                .earnedBadgeCodes(earnedCodes)
                .build();

        Map<String, BadgeAwardCandidate> candidateByCode = new HashMap<>();
        for (BadgeAwardCandidate candidate : badgeEngine.evaluateAllCandidates(context)) {
            candidateByCode.put(candidate.code(), candidate);
        }

        List<LockedBadgeDto> locked = new ArrayList<>();
        for (BadgeDefinition def : definitions) {
            if (earnedCodes.contains(def.getCode())) {
                continue;
            }
            BadgeAwardCandidate candidate = candidateByCode.get(def.getCode());
            LockedBadgeDto.LockedBadgeDtoBuilder builder = LockedBadgeDto.builder()
                    .code(def.getCode())
                    .name(def.getName())
                    .description(def.getDescription())
                    .category(def.getCategory())
                    .iconKey(def.getIconKey());
            if (candidate != null && candidate.progressTarget() > 0) {
                builder.progressCurrent(candidate.progressCurrent())
                        .progressTarget(candidate.progressTarget());
            }
            locked.add(builder.build());
        }

        return UserBadgesResponse.builder()
                .userId(userId)
                .earned(earned)
                .locked(locked)
                .build();
    }

    @Transactional(readOnly = true)
    public List<BadgeDefinitionDto> listBadgeDefinitions() {
        return badgeDefinitionRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(def -> BadgeDefinitionDto.builder()
                        .code(def.getCode())
                        .name(def.getName())
                        .description(def.getDescription())
                        .category(def.getCategory())
                        .iconKey(def.getIconKey())
                        .displayTier(def.getDisplayTier())
                        .build())
                .toList();
    }

    private ProgressResponse emptyProgress(String userId, boolean includeLeaderboardPosition) {
        RankTierResolver.TierView tier = RankTierResolver.describe(0, rankProperties.getTiers());
        return ProgressResponse.builder()
                .userId(userId)
                .algorithmVersion(RankConstants.ALGORITHM_VERSION)
                .staleVersion(false)
                .score(ScoreBreakdownDto.builder()
                        .mastery(0)
                        .potd(0)
                        .breadth(0)
                        .quality(0)
                        .contest(0)
                        .total(0)
                        .qualityStatus("DISABLED")
                        .contestStatus("DISABLED")
                        .build())
                .tier(toTierDto(tier))
                .metrics(ProgressMetricsDto.builder().build())
                .leaderboardPosition(includeLeaderboardPosition ? null : null)
                .calculatedAt(null)
                .build();
    }

    private ProgressResponse toProgressResponse(UserProgress progress, Long leaderboardPosition) {
        RankTierResolver.TierView tier = RankTierResolver.describe(
                progress.getTotalRankScore(), rankProperties.getTiers());
        return ProgressResponse.builder()
                .userId(progress.getUserId())
                .algorithmVersion(progress.getRankAlgorithmVersion())
                .staleVersion(!RankConstants.ALGORITHM_VERSION.equals(progress.getRankAlgorithmVersion()))
                .score(ScoreBreakdownDto.builder()
                        .mastery(progress.getMasteryScore())
                        .potd(progress.getPotdScore())
                        .breadth(progress.getBreadthScore())
                        .quality(progress.getQualityScore())
                        .contest(progress.getContestScore())
                        .total(progress.getTotalRankScore())
                        .qualityStatus("DISABLED")
                        .contestStatus("DISABLED")
                        .build())
                .tier(toTierDto(tier))
                .metrics(ProgressMetricsDto.builder()
                        .uniqueSolved(progress.getUniqueSolved())
                        .easySolved(progress.getEasySolved())
                        .mediumSolved(progress.getMediumSolved())
                        .hardSolved(progress.getHardSolved())
                        .totalPotdCompleted(progress.getTotalPotdCompleted())
                        .currentPotdStreak(progress.getCurrentPotdStreak())
                        .longestPotdStreak(progress.getLongestPotdStreak())
                        .breadthQualifiedTopics(progress.getBreadthQualifiedTopics())
                        .build())
                .leaderboardPosition(leaderboardPosition)
                .calculatedAt(progress.getCalculatedAt())
                .build();
    }

    private static TierDto toTierDto(RankTierResolver.TierView tier) {
        return TierDto.builder()
                .code(tier.code())
                .name(tier.name())
                .currentMinimum(tier.currentMinimum())
                .nextTierCode(tier.nextTierCode())
                .nextThreshold(tier.nextThreshold())
                .pointsToNext(tier.pointsToNext())
                .progressPercent(tier.progressPercent())
                .build();
    }

    private Long resolveLeaderboardPosition(UserProgress progress) {
        if (progress.getTotalRankScore() <= 0) {
            return null;
        }
        long better = userProgressRepository.countBetterThan(
                RankConstants.ALGORITHM_VERSION,
                progress.getTotalRankScore(),
                progress.getHardSolved(),
                progress.getMediumSolved(),
                progress.getTotalPotdCompleted());
        return better + 1;
    }

    private static UserProgress emptyUserProgress(String userId) {
        return UserProgress.builder()
                .userId(userId)
                .rankAlgorithmVersion(RankConstants.ALGORITHM_VERSION)
                .build();
    }
}
