package com.hrishabh.algocracksubmissionservice.progress.badge;

import com.hrishabh.algocracksubmissionservice.progress.model.UserProgress;
import com.hrishabh.algocracksubmissionservice.progress.model.UserTopicProgress;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
@Builder
public class BadgeEvaluationContext {
    String userId;
    UserProgress progress;
    List<UserTopicProgress> topicProgress;
    Set<String> earnedBadgeCodes;
}
