package com.hrishabh.algocracksubmissionservice.progress.badge.evaluators;

import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeAwardCandidate;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluationContext;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.badge.ThresholdBadgeSupport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DifficultyMilestoneBadgeEvaluator implements BadgeEvaluator {

    @Override
    public String category() {
        return "DIFFICULTY";
    }

    @Override
    public List<BadgeAwardCandidate> evaluate(BadgeEvaluationContext context) {
        return ThresholdBadgeSupport.difficulty(
                context.getProgress().getMediumSolved(),
                context.getProgress().getHardSolved());
    }
}
