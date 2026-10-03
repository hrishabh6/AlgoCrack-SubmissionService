package com.hrishabh.algocracksubmissionservice.progress.badge;

import java.util.List;

public interface BadgeEvaluator {

    String category();

    List<BadgeAwardCandidate> evaluate(BadgeEvaluationContext context);
}
