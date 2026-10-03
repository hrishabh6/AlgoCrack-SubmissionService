package com.hrishabh.algocracksubmissionservice.dto.progress;

import lombok.Builder;
import lombok.Value;

import java.util.List;

public final class QuestionRankMetadataApiDtos {

    private QuestionRankMetadataApiDtos() {
    }

    public record RankMetadataBatchRequest(List<Long> questionIds) {
    }

    @Value
    @Builder
    public static class TagRef {
        long tagId;
        String tagName;
    }

    @Value
    @Builder
    public static class QuestionRankMetadataItem {
        long questionId;
        String questionStatus;
        String difficultyLevel;
        String normalizedDifficulty;
        List<TagRef> tags;
    }

    @Value
    @Builder
    public static class RankMetadataBatchResponse {
        List<QuestionRankMetadataItem> items;
    }

    @Value
    @Builder
    public static class PotdResolveResponse {
        boolean matched;
        boolean qualifies;
        Long challengeId;
        String challengeDate;
        Long questionId;
        String challengeStatus;
        String difficultyLevel;
        String normalizedDifficulty;
    }
}
