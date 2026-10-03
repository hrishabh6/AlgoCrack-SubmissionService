package com.hrishabh.algocracksubmissionservice.progress.badge.evaluators;

import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeAwardCandidate;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluationContext;
import com.hrishabh.algocracksubmissionservice.progress.badge.BadgeEvaluator;
import com.hrishabh.algocracksubmissionservice.progress.badge.ThresholdBadgeSupport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PotdMilestoneBadgeEvaluator implements BadgeEvaluator {

    @Override
    public String category() {
        return "POTD";
    }

    @Override
    public List<BadgeAwardCandidate> evaluate(BadgeEvaluationContext context) {
        return ThresholdBadgeSupport.potd(context.getProgress().getTotalPotdCompleted());
    }
}
