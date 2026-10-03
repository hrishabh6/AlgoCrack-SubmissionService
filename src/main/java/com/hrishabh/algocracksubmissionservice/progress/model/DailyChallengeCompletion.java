package com.hrishabh.algocracksubmissionservice.progress.model;

import com.hrishabh.algocracksubmissionservice.models.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_challenge_completion", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dcc_user_challenge", columnNames = {"user_id", "daily_challenge_id"}),
        @UniqueConstraint(name = "uk_dcc_submission", columnNames = {"accepted_submission_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyChallengeCompletion extends BaseModel {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "daily_challenge_id", nullable = false)
    private long dailyChallengeId;

    @Column(name = "challenge_date", nullable = false)
    private LocalDate challengeDate;

    @Column(name = "question_id", nullable = false)
    private long questionId;

    @Column(name = "accepted_submission_id", nullable = false, length = 64)
    private String acceptedSubmissionId;

    @Column(name = "difficulty_snapshot", length = 20)
    private String difficultySnapshot;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
}
