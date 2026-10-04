package com.hrishabh.algocracksubmissionservice.complexity.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "complexity_static_finding", indexes = {
        @Index(name = "idx_complexity_finding_analysis", columnList = "analysisId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplexityStaticFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, length = 36, updatable = false)
    private String analysisId;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(name = "source_start_line")
    private Integer sourceStartLine;

    @Column(name = "source_end_line")
    private Integer sourceEndLine;

    @Column(length = 255)
    private String expression;

    @Column(length = 32)
    private String certainty;

    @Column(nullable = false, length = 512)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
