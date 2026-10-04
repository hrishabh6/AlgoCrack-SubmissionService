package com.hrishabh.algocracksubmissionservice.complexity.repository;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityStaticFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplexityStaticFindingRepository extends JpaRepository<ComplexityStaticFinding, Long> {

    void deleteByAnalysisId(String analysisId);

    List<ComplexityStaticFinding> findByAnalysisIdOrderByIdAsc(String analysisId);
}
