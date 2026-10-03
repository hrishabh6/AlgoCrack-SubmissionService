package com.hrishabh.algocracksubmissionservice.progress.badge;

import com.hrishabh.algocracksubmissionservice.progress.badge.evaluators.DifficultyMilestoneBadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.badge.evaluators.PotdMilestoneBadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.badge.evaluators.ProblemMilestoneBadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.badge.evaluators.StreakMilestoneBadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.badge.evaluators.TopicMilestoneBadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.config.BadgeProperties;
import com.hrishabh.algocracksubmissionservice.progress.config.RankProperties;
import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeEngineTest {

    private final BadgeEngine engine = new BadgeEngine(List.of(
            new ProblemMilestoneBadgeEvaluator(),
            new DifficultyMilestoneBadgeEvaluator(),
            new PotdMilestoneBadgeEvaluator(),
            new StreakMilestoneBadgeEvaluator(),
            new TopicMilestoneBadgeEvaluator(new RankProperties(), new BadgeProperties())));

    @Test
    void supportedCodesCoverAllSeededBadges() {
        assertThat(engine.supportedCodes()).containsExactlyInAnyOrderElementsOf(BadgeCodes.ALL);
    }

    @Test
    void awardsFirstSolveWhenUniqueSolvedReachesOne() {
        UserProgress progress = UserProgress.builder().uniqueSolved(1).build();
        BadgeEvaluationContext context = BadgeEvaluationContext.builder()
                .userId("u1")
                .progress(progress)
                .topicProgress(List.of())
                .earnedBadgeCodes(Set.of())
                .build();

        assertThat(engine.evaluateNewlyEarned(context)).containsExactly(BadgeCodes.FIRST_SOLVE);
    }
}
