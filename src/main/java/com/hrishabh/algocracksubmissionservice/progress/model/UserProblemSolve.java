package com.hrishabh.algocracksubmissionservice.progress.model;

import com.hrishabh.algocracksubmissionservice.models.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_problem_solve", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_problem_solve", columnNames = {"user_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProblemSolve extends BaseModel {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "question_id", nullable = false)
    private long questionId;

    @Column(name = "first_accepted_submission_id", nullable = false, length = 64)
    private String firstAcceptedSubmissionId;

    @Column(name = "first_accepted_at", nullable = false)
    private LocalDateTime firstAcceptedAt;

    @Column(name = "difficulty_snapshot", length = 20)
    private String difficultySnapshot;

    @Column(name = "question_status_snapshot", length = 20)
    private String questionStatusSnapshot;
}
