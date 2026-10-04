package com.hrishabh.algocracksubmissionservice.complexity.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ComplexityAnalysisOrchestratorTransactionScopeTest {

    @Test
    void advanceDoesNotOpenLongLivedTransactionBoundary() throws Exception {
        Method advance = ComplexityAnalysisOrchestrator.class.getDeclaredMethod("advance", String.class);
        assertFalse(advance.isAnnotationPresent(Transactional.class));
    }

    @Test
    void leaseLoadsUseRequiresNewTransactions() throws Exception {
        Method load = ComplexityAnalysisLeaseService.class.getDeclaredMethod("loadForLeaseWrite", String.class);
        Transactional tx = load.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertFalse(tx.readOnly());
    }
}
