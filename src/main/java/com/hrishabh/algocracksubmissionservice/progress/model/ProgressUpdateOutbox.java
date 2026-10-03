package com.hrishabh.algocracksubmissionservice.progress.model;

import com.hrishabh.algocracksubmissionservice.models.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "progress_update_outbox", uniqueConstraints = @UniqueConstraint(
        name = "uk_progress_outbox_event", columnNames = {"event_type", "source_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgressUpdateOutbox extends BaseModel {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ProgressEventType eventType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProgressOutboxStatus status = ProgressOutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 512)
    private String lastError;
}
