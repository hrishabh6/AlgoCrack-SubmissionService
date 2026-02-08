package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocrackentityservice.models.ReferenceSolution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for fetching oracle (reference solution) by question ID.
 */
public interface ReferenceSolutionRepository extends JpaRepository<ReferenceSolution, Long> {

    /**
     * Find the oracle for a given question.
     * Each question should have exactly one reference solution.
     */
    Optional<ReferenceSolution> findByQuestionId(Long questionId);
}
