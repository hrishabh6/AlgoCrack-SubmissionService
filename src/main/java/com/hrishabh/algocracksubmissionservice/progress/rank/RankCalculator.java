package com.hrishabh.algocracksubmissionservice.progress.rank;

import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Component
public class RankCalculator {

    private final RankProperties properties;

    public RankCalculator(RankProperties properties) {
        this.properties = properties;
        RankTierResolver.validateTierOrder(properties.getTiers());
    }

    public RankCalculationResult calculate(RankCalculationInput input, LocalDate todayUtc) {
        long mastery = input.getEasySolved() * properties.getMasteryEasy()
                + input.getMediumSolved() * properties.getMasteryMedium()
                + input.getHardSolved() * properties.getMasteryHard();

        long potd = input.getEasyPotdCompleted() * properties.getPotdEasy()
                + input.getMediumPotdCompleted() * properties.getPotdMedium()
                + input.getHardPotdCompleted() * properties.getPotdHard();

        Map<Long, BigDecimal> credits =
                input.getTopicCredits() != null ? input.getTopicCredits() : Map.of();
        long breadth = TopicBreadthCalculator.breadthScore(credits, properties);
        long quality = 0L;
        long contest = 0L;
        long total = mastery + potd + breadth + quality + contest;

        PotdStreakCalculator.StreakResult streak =
                PotdStreakCalculator.calculate(input.getPotdCompletionDatesUtc(), todayUtc);

        return RankCalculationResult.builder()
                .masteryScore(mastery)
                .potdScore(potd)
                .breadthScore(breadth)
                .qualityScore(quality)
                .contestScore(contest)
                .totalRankScore(total)
                .rankTierCode(RankTierResolver.resolveTierCode(total, properties.getTiers()))
                .currentPotdStreak(streak.currentStreak())
                .longestPotdStreak(streak.longestStreak())
                .breadthQualifiedTopics(TopicBreadthCalculator.countQualifiedTopics(
                        credits, properties.getBreadth().getExplorerCredit()))
                .build();
    }
}
