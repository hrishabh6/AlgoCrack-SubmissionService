package com.hrishabh.algocracksubmissionservice.statistics;

/**
 * Optional callback invoked after a persisted statistics row is loaded and before it is written.
 * Production does not register a bean. Tests commit a conflicting version here so optimistic-lock
 * retries are deterministic against the real database.
 */
@FunctionalInterface
public interface StatisticsVersionConflictProbe {
    void beforeWrite(Long questionId);
}
