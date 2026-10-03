package com.hrishabh.algocracksubmissionservice.progress.badge.evaluators;

import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeAwardCandidate;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluationContext;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.badge.ThresholdBadgeSupport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProblemMilestoneBadgeEvaluator implements BadgeEvaluator {

    @Override
    public String category() {
        return "PROBLEM";
    }

    @Override
    public List<BadgeAwardCandidate> evaluate(BadgeEvaluationContext context) {
        return ThresholdBadgeSupport.uniqueSolved(context.getProgress().getUniqueSolved());
    }
}
