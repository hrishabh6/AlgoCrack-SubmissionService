package com.hrishabh.algocracksubmissionservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks aggregate statistics for each question.
 * Used for displaying acceptance rates, average runtimes, etc.
 * Owned by SubmissionService.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "question_statistics", indexes = {
        @Index(name = "idx_stats_question_id", columnList = "questionId", unique = true)
})
public class QuestionStatistics extends BaseModel {

    @Column(nullable = false, unique = true)
    private Long questionId;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalSubmissions = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer acceptedSubmissions = 0;

    @Column
    private Integer avgRuntimeMs;

    @Column
    private Integer avgMemoryKb;

    @Column
    private Integer bestRuntimeMs;

    @Column
    private Integer bestMemoryKb;

    @Column
    private Integer uniqueAttempts;

    @Column
    private Integer uniqueSolves;

    @Column
    private Double avgAttemptsToSolve;

    @Column
    private LocalDateTime lastSubmissionAt;

    @Version
    private Long version;

    public Double getAcceptanceRate() {
        if (totalSubmissions == null || totalSubmissions == 0) {
            return 0.0;
        }
        return (acceptedSubmissions * 100.0) / totalSubmissions;
    }

    public void incrementSubmissions(boolean accepted, Integer runtimeMs, Integer memoryKb) {
        this.totalSubmissions++;
        if (accepted) {
            this.acceptedSubmissions++;
            if (runtimeMs != null) {
                if (this.bestRuntimeMs == null || runtimeMs < this.bestRuntimeMs) {
                    this.bestRuntimeMs = runtimeMs;
                }
            }
            if (memoryKb != null) {
                if (this.bestMemoryKb == null || memoryKb < this.bestMemoryKb) {
                    this.bestMemoryKb = memoryKb;
                }
            }
        }
        this.lastSubmissionAt = LocalDateTime.now();
    }
}
