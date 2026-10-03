package com.hrishabh.algocracksubmissionservice.progress.model;

import com.hrishabh.algocracksubmissionservice.models.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_progress", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_progress_version", columnNames = {"user_id", "rank_algorithm_version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProgress extends BaseModel {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "rank_algorithm_version", nullable = false, length = 32)
    private String rankAlgorithmVersion;

    @Column(name = "unique_solved", nullable = false)
    private long uniqueSolved;

    @Column(name = "easy_solved", nullable = false)
    private long easySolved;

    @Column(name = "medium_solved", nullable = false)
    private long mediumSolved;

    @Column(name = "hard_solved", nullable = false)
    private long hardSolved;

    @Column(name = "easy_potd_completed", nullable = false)
    private long easyPotdCompleted;

    @Column(name = "medium_potd_completed", nullable = false)
    private long mediumPotdCompleted;

    @Column(name = "hard_potd_completed", nullable = false)
    private long hardPotdCompleted;

    @Column(name = "total_potd_completed", nullable = false)
    private long totalPotdCompleted;

    @Column(name = "current_potd_streak", nullable = false)
    private int currentPotdStreak;

    @Column(name = "longest_potd_streak", nullable = false)
    private int longestPotdStreak;

    @Column(name = "breadth_qualified_topics", nullable = false)
    private int breadthQualifiedTopics;

    @Column(name = "mastery_score", nullable = false)
    private long masteryScore;

    @Column(name = "potd_score", nullable = false)
    private long potdScore;

    @Column(name = "breadth_score", nullable = false)
    private long breadthScore;

    @Column(name = "quality_score", nullable = false)
    private long qualityScore;

    @Column(name = "contest_score", nullable = false)
    private long contestScore;

    @Column(name = "total_rank_score", nullable = false)
    private long totalRankScore;

    @Column(name = "rank_tier_code", nullable = false, length = 32)
    private String rankTierCode;

    @Column(name = "source_watermark", length = 128)
    private String sourceWatermark;

    @Column(name = "score_achieved_at")
    private LocalDateTime scoreAchievedAt;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private long version = 0L;
}
