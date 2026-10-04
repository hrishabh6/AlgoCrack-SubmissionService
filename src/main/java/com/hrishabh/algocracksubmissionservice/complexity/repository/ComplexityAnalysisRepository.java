package com.hrishabh.algocracksubmissionservice.complexity.repository;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComplexityAnalysisRepository extends JpaRepository<ComplexityAnalysis, Long> {

    /**
     * Atomically claims a queued row for static analysis (safe across worker replicas).
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ComplexityAnalysis c
            SET c.status = com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus.STATIC_ANALYZING
            WHERE c.analysisId = :analysisId
              AND c.status = com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus.QUEUED
              AND c.activeSlot IS NOT NULL
            """)
    int claimQueuedForStaticAnalysis(@Param("analysisId") String analysisId);

    List<ComplexityAnalysis> findTop10ByStatusOrderByRequestedAtAsc(ComplexityProcessingStatus status);

    Optional<ComplexityAnalysis> findByAnalysisId(String analysisId);

    Optional<ComplexityAnalysis> findBySubmissionIdAndActiveSlot(String submissionId, Integer activeSlot);

    Optional<ComplexityAnalysis> findByAnalysisIdAndSubmissionId(String analysisId, String submissionId);

    Page<ComplexityAnalysis> findBySubmissionIdAndOwnerUserIdOrderByRequestedAtDesc(
            String submissionId, String ownerUserId, Pageable pageable);

    Optional<ComplexityAnalysis> findFirstBySubmissionIdAndAnalysisFingerprintAndStatusOrderByCompletedAtDesc(
            String submissionId, String analysisFingerprint, ComplexityProcessingStatus status);

    List<ComplexityAnalysis> findTop10ByStatusInAndActiveSlotIsNotNullOrderByRequestedAtAsc(
            List<ComplexityProcessingStatus> statuses);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ComplexityAnalysis c
            SET c.leaseOwner = :owner,
                c.leaseExpiresAt = :expiresAt,
                c.updatedAt = :now
            WHERE c.analysisId = :analysisId
              AND c.activeSlot IS NOT NULL
              AND (c.leaseExpiresAt IS NULL OR c.leaseExpiresAt <= :now OR c.leaseOwner = :owner)
            """)
    int claimLease(
            @Param("analysisId") String analysisId,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ComplexityAnalysis c
            SET c.leaseExpiresAt = :expiresAt,
                c.updatedAt = :now
            WHERE c.analysisId = :analysisId
              AND c.activeSlot IS NOT NULL
              AND c.leaseOwner = :owner
              AND c.leaseExpiresAt > :now
            """)
    int renewLease(
            @Param("analysisId") String analysisId,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM ComplexityAnalysis c
            WHERE c.analysisId = :analysisId
              AND c.activeSlot IS NOT NULL
              AND c.leaseOwner = :owner
              AND c.leaseExpiresAt > :now
            """)
    Optional<ComplexityAnalysis> findWithActiveLeaseLock(
            @Param("analysisId") String analysisId,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now);
}
