package com.hrishabh.algocracksubmissionservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tracks detailed execution metrics for analytics and monitoring.
 * Owned by SubmissionService.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "execution_metrics", indexes = {
        @Index(name = "idx_metrics_submission_id", columnList = "submissionId")
})
public class ExecutionMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String submissionId;

    @Column
    private Integer queueWaitMs;

    @Column
    private Integer compilationMs;

    @Column
    private Integer executionMs;

    @Column
    private Integer totalMs;

    @Column
    private Integer peakMemoryKb;

    @Column
    private Integer cpuTimeMs;

    @Column(length = 50)
    private String workerId;

    @Column(length = 100)
    private String executionNode;

    @Column
    private Boolean usedCache;

    @Column(length = 64)
    private String containerId;

    @Column(columnDefinition = "JSON")
    private String testCaseTimings;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
