package com.hrishabh.algocracksubmissionservice.statistics;

/**
 * Test hook invoked before reconciliation locks statistics and rereads submissions.
 * Production does not register a bean. The hook runs in the reconcile transaction
 * before those locks, so a test can commit an outbox apply in the race window.
 */
@FunctionalInterface
public interface StatisticsReconcileGap {
    void beforeLockedRead(Long questionId);
}
