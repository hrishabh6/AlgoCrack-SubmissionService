package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocrackentityservice.models.TestCase;
import com.hrishabh.algocrackentityservice.models.TestCaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TestcaseRepository extends JpaRepository<TestCase, Long> {

    /**
     * Find testcases by question ID and type.
     * Used to filter: DEFAULT (for RUN) or HIDDEN (for SUBMIT).
     */
    @Query("SELECT t FROM TestCase t WHERE t.question.id = :questionId AND t.type = :type")
    List<TestCase> findByQuestionIdAndType(Long questionId, TestCaseType type);

    /**
     * Find all testcases for a question (regardless of type).
     */
    @Query("SELECT t FROM TestCase t WHERE t.question.id = :questionId")
    List<TestCase> findByQuestionId(Long questionId);
}
