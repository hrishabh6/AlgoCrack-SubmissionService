package com.hrishabh.algocracksubmissionservice.service;

import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsApplier;
import com.hrishabh.algocracksubmissionservice.statistics.QuestionStatisticsReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SubmissionProcessingTransactionBoundaryTest {

    @Test
    void processingIsNotOneLongTransaction() throws Exception {
        Method process = SubmissionProcessingService.class.getMethod("processSubmission", String.class);
        assertFalse(process.isAnnotationPresent(Transactional.class));
    }

    @Test
    void verdictFinalizationCommitsBeforeStatistics() throws Exception {
        Method finalize = SubmissionFinalizer.class.getMethod(
                "finalizeSubmission", String.class, SubmissionCompletion.class);
        Transactional transactional = finalize.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRED, transactional.propagation());
    }

    @Test
    void statisticsAttemptUsesANewTransaction() throws Exception {
        Method apply = QuestionStatisticsApplier.class.getMethod("apply", long.class);
        Transactional transactional = apply.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());

        Method record = QuestionStatisticsApplier.class.getMethod("recordConflict", long.class, RuntimeException.class);
        assertEquals(Propagation.REQUIRES_NEW, record.getAnnotation(Transactional.class).propagation());
    }

    @Test
    void reconciliationAttemptUsesANewTransaction() throws Exception {
        Method reconcile = QuestionStatisticsReconciliationService.QuestionStatisticsReconcileTransaction.class
                .getMethod("reconcile", Long.class);
        Transactional transactional = reconcile.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }
}
