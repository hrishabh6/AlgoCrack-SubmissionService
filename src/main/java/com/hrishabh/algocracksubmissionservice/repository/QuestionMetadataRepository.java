package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocrackentityservice.models.QuestionMetadata;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuestionMetadataRepository extends JpaRepository<QuestionMetadata, Long> {
    // This query fetches the QuestionMetadata and both its paramTypes and paramNames collections in a single go.
    // 💡 Refactored query to join and fetch the single 'parameters' collection
    @Query("""
    SELECT qm 
    FROM QuestionMetadata qm 
    LEFT JOIN FETCH qm.parameters p 
    LEFT JOIN FETCH qm.customDataStructureNames c 
    WHERE qm.question.id = :questionId
""")
    Optional<QuestionMetadata> findByQuestionIdWithAllCollections(@Param("questionId") Long questionId);
}
