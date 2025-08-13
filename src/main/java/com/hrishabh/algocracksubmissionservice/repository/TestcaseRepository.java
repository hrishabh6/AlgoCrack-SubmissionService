package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocrackentityservice.models.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestcaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByQuestionIdOrderByOrderIndexAsc(long questionId);
}
