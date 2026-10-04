package com.hrishabh.algocracksubmissionservice.complexity.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "complexity_benchmark_run", indexes = {
        @Index(name = "idx_complexity_benchmark_analysis", columnList = "analysisId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplexityBenchmarkRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, length = 36, updatable = false)
    private String analysisId;

    @Column(name = "case_id", nullable = false, length = 64)
    private String caseId;

    @Column(nullable = false, length = 32)
    private String variant;

    @Column(name = "size_vector_json", nullable = false, columnDefinition = "json")
    private String sizeVectorJson;

    @Column
    private Long seed;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "warmup_count", nullable = false)
    private int warmupCount;

    @Column(name = "output_validated", nullable = false)
    private boolean outputValidated;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
