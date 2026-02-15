package com.hrishabh.algocracksubmissionservice.repository;

import com.hrishabh.algocrackentityservice.models.Language;
import com.hrishabh.algocrackentityservice.models.QuestionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuestionMetadataRepository extends JpaRepository<QuestionMetadata, Long> {

    /**
     * Fetch QuestionMetadata by question ID and language.
     * A question can have multiple metadata records (one per language).
     */
    @Query("""
                SELECT qm
                FROM QuestionMetadata qm
                WHERE qm.question.id = :questionId AND qm.language = :language
            """)
    Optional<QuestionMetadata> findByQuestionIdAndLanguage(
            @Param("questionId") Long questionId,
            @Param("language") Language language);

    /**
     * Fetch QuestionMetadata with Question entity eagerly loaded.
     * Needed for judging context (nodeType, isOutputOrderMatters from Question).
     */
    @Query("""
                SELECT qm
                FROM QuestionMetadata qm
                JOIN FETCH qm.question q
                WHERE q.id = :questionId AND qm.language = :language
            """)
    Optional<QuestionMetadata> findByQuestionIdAndLanguageWithQuestion(
            @Param("questionId") Long questionId,
            @Param("language") Language language);
}
