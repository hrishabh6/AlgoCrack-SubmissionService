package com.hrishabh.algocracksubmissionservice.progress.badge;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class BadgeEngine {

    private final List<BadgeEvaluator> evaluators;

    public BadgeEngine(List<BadgeEvaluator> evaluators) {
        this.evaluators = evaluators;
    }

    public Set<String> supportedCodes() {
        Set<String> codes = new HashSet<>();
        BadgeEvaluationContext empty = BadgeEvaluationContext.builder()
                .userId("probe")
                .progress(emptyProgress())
                .topicProgress(List.of())
                .earnedBadgeCodes(Set.of())
                .build();
        for (BadgeEvaluator evaluator : evaluators) {
            for (BadgeAwardCandidate candidate : evaluator.evaluate(empty)) {
                codes.add(candidate.code());
            }
        }
        return codes;
    }

    public List<BadgeAwardCandidate> evaluateAllCandidates(BadgeEvaluationContext context) {
        List<BadgeAwardCandidate> candidates = new java.util.ArrayList<>();
        for (BadgeEvaluator evaluator : evaluators) {
            candidates.addAll(evaluator.evaluate(context));
        }
        return candidates;
    }

    public Set<String> evaluateNewlyEarned(BadgeEvaluationContext context) {
        Set<String> earnedAlready = context.getEarnedBadgeCodes() != null
                ? context.getEarnedBadgeCodes()
                : Set.of();
        Set<String> newlyEarned = new HashSet<>();
        for (BadgeEvaluator evaluator : evaluators) {
            for (BadgeAwardCandidate candidate : evaluator.evaluate(context)) {
                if (candidate.earned() && !earnedAlready.contains(candidate.code())) {
                    newlyEarned.add(candidate.code());
                }
            }
        }
        return newlyEarned;
    }

    private static com.hrishabh.algocracksubmissionservice.progress.model.UserProgress emptyProgress() {
        return com.hrishabh.algocracksubmissionservice.progress.model.UserProgress.builder()
                .uniqueSolved(0)
                .mediumSolved(0)
                .hardSolved(0)
                .totalPotdCompleted(0)
                .longestPotdStreak(0)
                .build();
    }
}
