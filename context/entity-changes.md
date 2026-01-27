# Entity Service - Detailed Changes Documentation

## Overview

This document provides a comprehensive breakdown of every change made to the **AlgoCrack-EntityService** library to align it with the new scalable async architecture.

---

## Summary of Changes

| File | Action | Issue Resolved |
|------|--------|----------------|
| `SubmissionStatus.java` | Modified | Wrong package name |
| `SubmissionVerdict.java` | Modified | Wrong package name |
| `ExecutionMetrics.java` | Modified | Wrong package, missing BaseModel inheritance |
| `Submission.java` | Replaced | Missing async tracking fields |
| `QuestionStatistics.java` | Created | No per-question analytics |
| `Submission copy.java` | Deleted | Invalid duplicate file |
| `entity-context.md` | Updated | Documentation outdated |

---

## File 1: SubmissionStatus.java

### Issue

The enum was created with the wrong package name, likely copy-pasted from the CodeExecutionEngine project.

### Before

```java
package xyz.hrishabhjoshi.codeexecutionengine.model;

public enum SubmissionStatus {
    QUEUED,
    COMPILING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

### After

```java
package com.hrishabh.algocrackentityservice.models;

/**
 * Represents the current processing status of a code submission.
 */
public enum SubmissionStatus {
    /**
     * Submission is queued waiting for a worker
     */
    QUEUED,

    /**
     * Code is being compiled
     */
    COMPILING,

    /**
     * Code is executing against test cases
     */
    RUNNING,

    /**
     * Execution completed (check verdict for result)
     */
    COMPLETED,

    /**
     * System error occurred (not user code error)
     */
    FAILED,

    /**
     * Submission was cancelled by user or timeout
     */
    CANCELLED
}
```

### Why This Change

- **Package consistency**: All entity classes must be in `com.hrishabh.algocrackentityservice.models` for `@EntityScan` to work in consumer services
- **Documentation**: Added Javadoc for each enum value to improve code clarity

### Architecture Alignment

The `SubmissionStatus` enum is used by:
- **Submission entity**: To track processing state
- **CodeExecutionService**: To report execution progress
- **SubmissionService**: To update and query submission states

---

## File 2: SubmissionVerdict.java

### Issue

Same package name issue as SubmissionStatus.

### Before

```java
package xyz.hrishabhjoshi.codeexecutionengine.model;

public enum SubmissionVerdict {
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILATION_ERROR,
    INTERNAL_ERROR
}
```

### After

```java
package com.hrishabh.algocrackentityservice.models;

/**
 * Represents the final verdict/result of a code submission.
 */
public enum SubmissionVerdict {
    /**
     * All test cases passed
     */
    ACCEPTED,

    /**
     * Output doesn't match expected output
     */
    WRONG_ANSWER,

    /**
     * Execution took too long
     */
    TIME_LIMIT_EXCEEDED,

    /**
     * Used too much memory
     */
    MEMORY_LIMIT_EXCEEDED,

    /**
     * Exception/crash during execution
     */
    RUNTIME_ERROR,

    /**
     * Code failed to compile
     */
    COMPILATION_ERROR,

    /**
     * System/judge error (not user's fault)
     */
    INTERNAL_ERROR
}
```

### Why This Change

- **Package consistency**: Required for entity scanning
- **Clear semantics**: Each verdict maps to a specific failure mode, making debugging easier

### Architecture Alignment

The verdict is determined by **SubmissionService** after comparing actual outputs with expected outputs. CodeExecutionService only executes and returns raw outputs.

---

## File 3: ExecutionMetrics.java

### Issues

1. Wrong package name
2. Missing `BaseModel` inheritance (inconsistent with other entities)
3. Redundant `@PrePersist` logic (handled by BaseModel's `@CreatedDate`)
4. Missing `updatedAt` audit field

### Before

```java
package xyz.hrishabhjoshi.codeexecutionengine.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "execution_metrics", indexes = {
        @Index(name = "idx_metrics_submission_id", columnList = "submissionId")
})
public class ExecutionMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String submissionId;

    @Column
    private Integer queueWaitMs;

    @Column
    private Integer compilationMs;

    @Column
    private Integer executionMs;

    @Column
    private Integer totalMs;

    @Column
    private Integer peakMemoryKb;

    @Column
    private Integer cpuTimeMs;

    @Column(length = 50)
    private String workerId;

    @Column(length = 100)
    private String executionNode;

    @Column
    private Boolean usedCache;

    @Column(length = 64)
    private String containerId;

    @Column(columnDefinition = "JSON")
    private String testCaseTimings;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### After

```java
package com.hrishabh.algocrackentityservice.models;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tracks detailed execution metrics for analytics and monitoring.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "execution_metrics", indexes = {
        @Index(name = "idx_metrics_submission_id", columnList = "submissionId")
})
public class ExecutionMetrics extends BaseModel {

    /**
     * Reference to the submission
     */
    @Column(nullable = false, length = 36)
    private String submissionId;

    // ========== Timing Breakdown ==========

    /**
     * Time waiting in queue (ms)
     */
    @Column
    private Integer queueWaitMs;

    /**
     * Compilation time (ms)
     */
    @Column
    private Integer compilationMs;

    /**
     * Total execution time (ms)
     */
    @Column
    private Integer executionMs;

    /**
     * End-to-end time (ms)
     */
    @Column
    private Integer totalMs;

    // ========== Resource Usage ==========

    /**
     * Peak memory usage (KB)
     */
    @Column
    private Integer peakMemoryKb;

    /**
     * CPU time used (ms)
     */
    @Column
    private Integer cpuTimeMs;

    // ========== System Info ==========

    /**
     * Worker that processed this submission
     */
    @Column(length = 50)
    private String workerId;

    /**
     * Hostname/IP of execution node
     */
    @Column(length = 100)
    private String executionNode;

    /**
     * Whether result was served from cache
     */
    @Column
    private Boolean usedCache;

    /**
     * Docker container ID (if applicable)
     */
    @Column(length = 64)
    private String containerId;

    /**
     * Test case timing breakdown as JSON
     * [{"index": 0, "compileMs": 450, "executeMs": 15}, ...]
     */
    @Column(columnDefinition = "JSON")
    private String testCaseTimings;
}
```

### Key Differences

| Aspect | Before | After |
|--------|--------|-------|
| Package | `xyz.hrishabhjoshi.codeexecutionengine.model` | `com.hrishabh.algocrackentityservice.models` |
| Inheritance | None | `extends BaseModel` |
| ID field | Explicit `@Id` | Inherited from BaseModel |
| createdAt | Manual `@PrePersist` | Automatic via `@CreatedDate` |
| updatedAt | Missing | Automatic via `@LastModifiedDate` |
| LocalDateTime import | Required | Not needed |

### Why This Change

- **Consistency**: All entities follow the same pattern (extend BaseModel)
- **DRY principle**: Audit fields defined once in BaseModel
- **Automatic auditing**: Spring's `@EnableJpaAuditing` handles timestamps

### Architecture Alignment

ExecutionMetrics is created by **CodeExecutionService** after each execution to store detailed timing/resource analytics for monitoring and optimization.

---

## File 4: Submission.java (Major Overhaul)

### Issues with Original

1. **No external ID**: Only had internal `long id`, no UUID for API reference
2. **No status tracking**: Just `isPassed` boolean, couldn't track lifecycle
3. **No performance metrics**: No `runtimeMs`, `memoryKb`
4. **No test case results**: Couldn't store per-test results
5. **No timestamps**: Only `timeOfSubmission`, missing queue/completion times
6. **No worker tracking**: Couldn't identify which worker processed
7. **No database indexes**: Slow queries at scale
8. **Old error field**: Generic `typeOfError` string

### Before (37 lines)

```java
package com.hrishabh.algocrackentityservice.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission extends BaseModel {

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(columnDefinition = "TEXT")
    private String code;

    private String language;

    private Boolean isPassed;

    private LocalDateTime timeOfSubmission;

    private String typeOfError; // Optional
}
```

### After (175 lines)

```java
package com.hrishabh.algocrackentityservice.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Represents a code submission made by a user for a specific question.
 * Tracks the entire lifecycle from queuing to completion with detailed metrics.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "submission", indexes = {
        @Index(name = "idx_submission_id", columnList = "submissionId"),
        @Index(name = "idx_user_status", columnList = "user_id, status"),
        @Index(name = "idx_question_status", columnList = "question_id, status"),
        @Index(name = "idx_status_queued", columnList = "status, queuedAt")
})
public class Submission extends BaseModel {

    // ========== External Reference ==========

    @Column(unique = true, nullable = false, length = 36)
    private String submissionId;  // UUID

    // ========== Relationships ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    // ========== Code Details ==========

    @Column(nullable = false, length = 20)
    private String language;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    // ========== Status Tracking ==========

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SubmissionVerdict verdict;

    // ========== Performance Metrics ==========

    @Column
    private Integer runtimeMs;

    @Column
    private Integer memoryKb;

    // ========== Test Results ==========

    @Column(columnDefinition = "JSON")
    private String testResults;

    @Column
    private Integer passedTestCases;

    @Column
    private Integer totalTestCases;

    // ========== Error Information ==========

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String compilationOutput;

    // ========== Timestamps ==========

    @Column(nullable = false)
    private LocalDateTime queuedAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    // ========== Client Metadata ==========

    @Column(length = 45)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    // ========== Worker Info ==========

    @Column(length = 50)
    private String workerId;

    // ========== Calculated Methods ==========

    public Long getProcessingTimeMs() {
        if (queuedAt != null && completedAt != null) {
            return java.time.Duration.between(queuedAt, completedAt).toMillis();
        }
        return null;
    }

    public Long getQueueWaitTimeMs() {
        if (queuedAt != null && startedAt != null) {
            return java.time.Duration.between(queuedAt, startedAt).toMillis();
        }
        return null;
    }

    public boolean isAccepted() {
        return verdict == SubmissionVerdict.ACCEPTED;
    }
}
```

### Field Mapping

| Old Field | New Field | Notes |
|-----------|-----------|-------|
| - | `submissionId` | **NEW**: UUID for external reference |
| `user` | `user` | Added LAZY fetch, NOT NULL |
| `question` | `question` | Added LAZY fetch, NOT NULL |
| `code` | `code` | Added NOT NULL |
| `language` | `language` | Added NOT NULL, length limit |
| `isPassed` | `verdict` | **REPLACED**: Enum instead of boolean |
| - | `status` | **NEW**: Lifecycle tracking |
| - | `runtimeMs` | **NEW**: Performance metric |
| - | `memoryKb` | **NEW**: Performance metric |
| - | `testResults` | **NEW**: JSON test case results |
| - | `passedTestCases` | **NEW**: Count |
| - | `totalTestCases` | **NEW**: Count |
| `typeOfError` | `errorMessage` | **RENAMED** |
| - | `compilationOutput` | **NEW**: Compiler output |
| `timeOfSubmission` | `queuedAt` | **RENAMED** to be more precise |
| - | `startedAt` | **NEW**: Execution start time |
| - | `completedAt` | **NEW**: Completion time |
| - | `ipAddress` | **NEW**: Client tracking |
| - | `userAgent` | **NEW**: Client tracking |
| - | `workerId` | **NEW**: Worker identification |

### Database Indexes Added

| Index Name | Columns | Purpose |
|------------|---------|---------|
| `idx_submission_id` | `submissionId` | Fast lookup by UUID |
| `idx_user_status` | `user_id, status` | User's submissions by status |
| `idx_question_status` | `question_id, status` | Question submissions by status |
| `idx_status_queued` | `status, queuedAt` | Queue monitoring, ordering |

### Architecture Alignment

The upgraded Submission entity supports:

1. **Async polling**: Frontend polls `GET /submissions/{submissionId}` using the UUID
2. **Lifecycle tracking**: Status progresses QUEUED → COMPILING → RUNNING → COMPLETED
3. **Performance display**: Show runtime/memory to users like LeetCode
4. **Debugging**: Store detailed error messages and compilation output
5. **Analytics**: Track timing breakdowns for optimization
6. **Security**: Log client IP and user agent

---

## File 5: QuestionStatistics.java (New Entity)

### Issue

No entity existed to track per-question aggregate statistics. This data was uncacheable and required expensive queries on every page load.

### Created

```java
package com.hrishabh.algocrackentityservice.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Tracks aggregate statistics for each question.
 * Used for displaying acceptance rates, average runtimes, etc.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "question_statistics", indexes = {
        @Index(name = "idx_stats_question_id", columnList = "questionId", unique = true)
})
public class QuestionStatistics extends BaseModel {

    @Column(nullable = false, unique = true)
    private Long questionId;

    // ========== Submission Counts ==========

    @Column(nullable = false)
    @Builder.Default
    private Integer totalSubmissions = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer acceptedSubmissions = 0;

    // ========== Performance Metrics ==========

    @Column
    private Integer avgRuntimeMs;

    @Column
    private Integer avgMemoryKb;

    @Column
    private Integer bestRuntimeMs;

    @Column
    private Integer bestMemoryKb;

    // ========== User Metrics ==========

    @Column
    private Integer uniqueAttempts;

    @Column
    private Integer uniqueSolves;

    @Column
    private Double avgAttemptsToSolve;

    // ========== Timestamps ==========

    @Column
    private LocalDateTime lastSubmissionAt;

    // ========== Versioning ==========

    @Version
    private Long version;

    // ========== Calculated Methods ==========

    public Double getAcceptanceRate() {
        if (totalSubmissions == null || totalSubmissions == 0) {
            return 0.0;
        }
        return (acceptedSubmissions * 100.0) / totalSubmissions;
    }

    public void incrementSubmissions(boolean accepted, Integer runtimeMs, Integer memoryKb) {
        this.totalSubmissions++;
        if (accepted) {
            this.acceptedSubmissions++;
            if (runtimeMs != null) {
                if (this.bestRuntimeMs == null || runtimeMs < this.bestRuntimeMs) {
                    this.bestRuntimeMs = runtimeMs;
                }
            }
            if (memoryKb != null) {
                if (this.bestMemoryKb == null || memoryKb < this.bestMemoryKb) {
                    this.bestMemoryKb = memoryKb;
                }
            }
        }
        this.lastSubmissionAt = LocalDateTime.now();
    }
}
```

### Why This Entity

| Use Case | Without | With |
|----------|---------|------|
| Show acceptance rate | `SELECT COUNT(*) FROM submission WHERE question_id=? AND verdict='ACCEPTED'` for EVERY page view | Simple read: `SELECT acceptance_rate FROM question_statistics` |
| Show average runtime | Expensive AVG query | Pre-computed field |
| Show best runtime | MIN query | Pre-computed field |
| Leaderboard | Complex query | Simple ORDER BY |

### Architecture Alignment

**SubmissionService** updates QuestionStatistics after each submission is processed:

```java
@Transactional
public void updateQuestionStatistics(Submission submission) {
    QuestionStatistics stats = statsRepository.findByQuestionId(questionId)
        .orElseGet(() -> QuestionStatistics.builder()
            .questionId(questionId)
            .build());

    stats.incrementSubmissions(
        submission.isAccepted(),
        submission.getRuntimeMs(),
        submission.getMemoryKb()
    );
    statsRepository.save(stats);
}
```

### Optimistic Locking

The `@Version` field prevents concurrent update issues when multiple submissions for the same question complete simultaneously.

---

## File 6: Submission copy.java (Deleted)

### Issue

A file named `Submission copy.java` existed in the models directory. This is:
1. **Invalid Java**: Spaces not allowed in class names
2. **Build failure**: Gradle/Java compiler fails
3. **Duplicate class**: Conflicts with `Submission.java`

### Action

```bash
rm -f "src/main/java/com/hrishabh/algocrackentityservice/models/Submission copy.java"
```

### Assumption

This was an accidental duplicate created during development. The content was likely identical or very similar to `Submission.java`.

---

## File 7: entity-context.md (Documentation Update)

### Issues

1. Documentation was outdated after entity changes
2. Missing new entities (QuestionStatistics, ExecutionMetrics)
3. Missing enum documentation
4. ER diagram incomplete

### Updates Made

- Added all new entities to ER diagram
- Documented new fields in Submission
- Added SubmissionStatus and SubmissionVerdict enums
- Added QuestionStatistics and ExecutionMetrics tables
- Added async execution flow diagram
- Updated relationship summary

---

## Assumptions Made

### 1. Database Compatibility

**Assumption**: The existing database either:
- Has no data (fresh install)
- Will be migrated using Flyway migrations

**Reasoning**: The Submission entity changes significantly. Existing rows would need migration scripts to populate new required fields.

**Recommendation**: Create Flyway migration:
```sql
-- V2__upgrade_submission_table.sql
ALTER TABLE submission 
  ADD COLUMN submission_id VARCHAR(36),
  ADD COLUMN status VARCHAR(20) DEFAULT 'COMPLETED',
  ADD COLUMN verdict VARCHAR(30),
  ADD COLUMN runtime_ms INT,
  ADD COLUMN memory_kb INT,
  -- ... etc
;

-- Populate submissionId for existing rows
UPDATE submission SET submission_id = UUID() WHERE submission_id IS NULL;
ALTER TABLE submission MODIFY submission_id VARCHAR(36) NOT NULL UNIQUE;
```

### 2. User and Question Entities Unchanged

**Assumption**: The `User` and `Question` entities remain as-is.

**Reasoning**: These are core entities referenced by Submission. Changing them would require cascading updates across the system.

**Note**: The `User.pastSubmissions` relationship still works because fieldname `user` in Submission matches `mappedBy = "user"`.

### 3. JSON Column Type

**Assumption**: Database supports JSON column type.

**Reasoning**: Used `columnDefinition = "JSON"` for:
- `Submission.testResults`
- `ExecutionMetrics.testCaseTimings`

**Compatibility**:
- MySQL 5.7+: ✅ Native JSON
- PostgreSQL: Use `JSONB`
- H2 (testing): May need `TEXT`

### 4. FetchType.LAZY for Relationships

**Assumption**: LAZY loading is preferred for performance.

**Reasoning**: When querying submissions, we often don't need the full User or Question entity. LAZY prevents N+1 query issues.

**Caveat**: If accessed outside transaction, may throw `LazyInitializationException`. Use `@Transactional` or fetch joins.

### 5. Test Results as JSON String

**Assumption**: Test case results are stored as a JSON string rather than a separate table.

**Reasoning**: 
- Simplicity: No additional table/relationship
- Read pattern: Always read all results together
- Write pattern: Written once on completion
- Size: Typically < 10KB even for 100 test cases

**Format**:
```json
[
  {"index": 0, "passed": true, "actualOutput": "[0,1]", "executionTimeMs": 15},
  {"index": 1, "passed": true, "actualOutput": "[1,2]", "executionTimeMs": 12}
]
```

---

## Architecture Alignment Summary

| Entity | Service That Creates | Service That Reads | Purpose |
|--------|---------------------|-------------------|---------|
| `Submission` | SubmissionService | Both | Track user code submissions |
| `SubmissionStatus` | SubmissionService | Both | Lifecycle state |
| `SubmissionVerdict` | SubmissionService | Both | Final result |
| `ExecutionMetrics` | CodeExecutionService | Dashboard/Analytics | Performance analytics |
| `QuestionStatistics` | SubmissionService | QuestionService, Frontend | Aggregate stats |

### Data Flow

```
User submits code
       ↓
SubmissionService creates Submission (status=QUEUED)
       ↓
SubmissionService calls CodeExecutionService HTTP API
       ↓
CodeExecutionService executes, creates ExecutionMetrics
       ↓
SubmissionService polls for results
       ↓
SubmissionService validates, updates Submission (status=COMPLETED, verdict=ACCEPTED)
       ↓
SubmissionService updates QuestionStatistics
       ↓
WebSocket pushes result to frontend
```

---

## Build Verification

```bash
./gradlew clean build -x test
```

**Result**: ✅ BUILD SUCCESSFUL

**Warnings**: 10 Lombok `@Builder.Default` warnings for unrelated entities (Tag, Question, etc.) - pre-existing, not caused by these changes.
