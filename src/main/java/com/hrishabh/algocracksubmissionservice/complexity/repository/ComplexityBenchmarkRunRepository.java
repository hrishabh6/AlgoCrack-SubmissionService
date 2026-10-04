package com.hrishabh.algocracksubmissionservice.complexity.repository;

import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityBenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplexityBenchmarkRunRepository extends JpaRepository<ComplexityBenchmarkRun, Long> {
}
