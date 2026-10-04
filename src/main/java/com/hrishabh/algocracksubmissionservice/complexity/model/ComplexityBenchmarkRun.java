package com.hrishabh.algocracksubmissionservice.complexity.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "case_identity", nullable = false, length = 128)
    private String caseIdentity;

    @Column(name = "profile_version", length = 32)
    private String profileVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "profile_hash", length = 64)
    private String profileHash;

    @Column(name = "generator_version", length = 32)
    private String generatorVersion;

    @Column(name = "harness_version", length = 32)
    private String harnessVersion;

    @Column(name = "measurement_policy_version", length = 32)
    private String measurementPolicyVersion;

    @Column(nullable = false, length = 32)
    private String variant;

    @Column(name = "size_vector_json", nullable = false, columnDefinition = "json")
    private String sizeVectorJson;

    @Column
    private Long seed;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "warmup_count", nullable = false)
    private int warmupCount;

    @Column(name = "median_elapsed_ns")
    private Long medianElapsedNs;

    @Column(name = "mad_elapsed_ns")
    private Long madElapsedNs;

    @Column(name = "min_elapsed_ns")
    private Long minElapsedNs;

    @Column(name = "max_elapsed_ns")
    private Long maxElapsedNs;

    @Column(name = "output_validated", nullable = false)
    private boolean outputValidated;

    @Column(name = "environment_fingerprint", length = 128)
    private String environmentFingerprint;

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
