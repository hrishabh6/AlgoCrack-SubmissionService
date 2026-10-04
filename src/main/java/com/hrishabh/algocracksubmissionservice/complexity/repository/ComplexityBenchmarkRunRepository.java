package com.hrishabh.algocracksubmissionservice.complexity.repository;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityBenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplexityBenchmarkRunRepository extends JpaRepository<ComplexityBenchmarkRun, Long> {

    java.util.List<ComplexityBenchmarkRun> findByAnalysisId(String analysisId);

    java.util.Optional<ComplexityBenchmarkRun> findByAnalysisIdAndCaseIdentity(String analysisId, String caseIdentity);
}
