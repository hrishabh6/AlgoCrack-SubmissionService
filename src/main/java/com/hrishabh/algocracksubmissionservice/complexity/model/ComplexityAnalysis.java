package com.hrishabh.algocracksubmissionservice.complexity.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "complexity_analysis", indexes = {
        @Index(name = "idx_complexity_owner_requested", columnList = "ownerUserId, requestedAt"),
        @Index(name = "idx_complexity_submission_requested", columnList = "submissionId, requestedAt"),
        @Index(name = "idx_complexity_status_next_attempt", columnList = "status, nextAttemptAt"),
        @Index(name = "idx_complexity_reuse_lookup", columnList = "submissionId, analysisFingerprint, completedAt")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_complexity_analysis_id", columnNames = "analysisId"),
        @UniqueConstraint(name = "uk_complexity_submission_active_slot", columnNames = {"submissionId", "activeSlot"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplexityAnalysis {

    public static final int ACTIVE_SLOT_VALUE = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, length = 36, updatable = false)
    private String analysisId;

    @Column(name = "submission_id", nullable = false, length = 36, updatable = false)
    private String submissionId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private String ownerUserId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Long questionId;

    @Column(nullable = false, length = 20, updatable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ComplexityProcessingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_kind", length = 32)
    private ComplexityResultKind resultKind;

    @Column(name = "source_sha256", nullable = false, length = 64, updatable = false)
    private String sourceSha256;

    @Column(name = "analysis_fingerprint", length = 128)
    private String analysisFingerprint;

    @Column(name = "reused_from_analysis_id", length = 36)
    private String reusedFromAnalysisId;

    @Column(name = "analyzer_version", length = 32)
    private String analyzerVersion;

    @Column(name = "confidence_model_version", length = 32)
    private String confidenceModelVersion;

    @Column(name = "knowledge_base_version", length = 32)
    private String knowledgeBaseVersion;

    @Column(name = "time_expression", length = 255)
    private String timeExpression;

    @Column(name = "time_big_o", length = 64)
    private String timeBigO;

    @Column(name = "time_bound_basis", length = 32)
    private String timeBoundBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_confidence", length = 16)
    private ComplexityConfidence timeConfidence;

    @Column(name = "space_expression", length = 255)
    private String spaceExpression;

    @Column(name = "space_big_o", length = 64)
    private String spaceBigO;

    @Enumerated(EnumType.STRING)
    @Column(name = "space_confidence", length = 16)
    private ComplexityConfidence spaceConfidence;

    @Column(name = "limitations_json", columnDefinition = "json")
    private String limitationsJson;

    @Column(name = "variable_definitions_json", columnDefinition = "json")
    private String variableDefinitionsJson;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "active_slot")
    private Integer activeSlot;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "optimistic_version", nullable = false)
    private Long optimisticVersion;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (requestedAt == null) {
            requestedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return activeSlot != null;
    }
}
