package com.hrishabh.algocracksubmissionservice.complexity.repository;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityProcessingStatus;

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
}
