# Problem Service - Architecture Improvement Plan

## Executive Summary

This document outlines the improvements needed for **AlgoCrack-ProblemService** to align with the new scalable microservice architecture. While the Problem Service is fundamentally well-designed for its purpose (CRUD operations), it has several flaws and gaps that need to be addressed for production readiness and compatibility with the upgraded ecosystem.

---

## Part 1: Current Flaws and Issues

### 1.1 Port Conflict ⚠️

| Issue | Current | Expected |
|-------|---------|----------|
| Server Port | `8080` | `8082` |

**Problem**: Problem Service runs on port 8080, which conflicts with Submission Service.

**Impact**: Can't run both services simultaneously.

---

### 1.2 Missing Pagination Support

**Problem**: No pagination for listing questions.

```java
// Current: No list endpoint exists!
// Missing: GET /api/v1/questions?page=0&size=20
```

**Impact**: 
- Can't browse all questions
- Memory issues with large datasets
- Poor UX for frontend

---

### 1.3 No Search/Filter Capabilities

**Problem**: Cannot search questions by title, difficulty, tag, or company.

**Impact**:
- Frontend can't implement search
- Poor user experience
- Can't filter by difficulty/tag

---

### 1.4 Incomplete CRUD APIs

| Resource | Create | Read | Update | Delete | List |
|----------|--------|------|--------|--------|------|
| Questions | ✅ | ✅ | ✅ | ✅ | ❌ |
| Test Cases | ✅ | ❌ | ❌ | ❌ | ❌ |
| Solutions | ❌ | ❌ | ✅ | ❌ | ❌ |
| Tags | ✅ | ❌ | ❌ | ❌ | ❌ |
| Metadata | ❌ | ❌ | ❌ | ❌ | ❌ |

**Impact**: Can't manage individual resources after creation.

---

### 1.5 Unused SubmissionsRepository

```java
// This exists but is never used:
public interface SubmissionsRepository extends JpaRepository<Submission, Long> {}
```

**Problem**: Repository for submissions exists in Problem Service but should be in Submission Service only.

**Impact**: Violates single responsibility principle.

---

### 1.6 Old EntityService Version

```groovy
// Current (JitPack URL pattern):
implementation 'com.github.hrishabh6:AlgoCrack-EntityService:v1.0:plain'

// Should be (mavenLocal pattern):
implementation 'com.hrishabh:AlgoCrack-EntityService:0.0.8-SNAPSHOT'
```

**Problem**: Using old entity library without new entities:
- `Submission` (upgraded with status, verdict, metrics)
- `SubmissionStatus` enum
- `SubmissionVerdict` enum  
- `QuestionStatistics` entity
- `ExecutionMetrics` entity

---

### 1.7 Inconsistent Exception Handling

```java
// Sometimes throws RuntimeException:
throw new RuntimeException("Question not found with ID: " + id);

// Sometimes throws ResourceNotFoundException:
throw new ResourceNotFoundException("Question not found with ID: " + id);
```

**Impact**: Inconsistent error responses to clients.

---

### 1.8 No Global Exception Handler

**Problem**: Missing `@ControllerAdvice` for consistent error responses.

**Impact**: 
- Raw stack traces exposed to clients
- Inconsistent error formats
- No proper HTTP status codes for errors

---

### 1.9 Missing API Versioning Strategy

**Problem**: API prefix is `/api/v1/` but no strategy for future versions.

**Impact**: Breaking changes will affect all clients.

---

### 1.10 No Validation Annotations

```java
// Current - manual validation in service:
if (dto.getQuestionTitle() == null || dto.getQuestionTitle().isBlank()) {
    return ResponseEntity.badRequest()...
}

// Should use @Valid annotations:
public ResponseEntity<?> createQuestion(@Valid @RequestBody QuestionRequestDto dto)
```

**Impact**: Verbose code, easy to miss validations.

---

### 1.11 ddl-auto=update in Production 

```properties
spring.jpa.hibernate.ddl-auto=update
```

**Problem**: Schema auto-update is dangerous in production.

**Impact**: Potential data loss, unpredictable schema changes.

---

### 1.12 Missing QuestionMetadata Enhancements

Current `QuestionMetadata` lacks fields needed by CXE:

| Missing Field | Purpose |
|--------------|---------|
| `parameters` | List<Parameter> instead of separate lists |
| `customDataStructureNames` | Set of custom class names |
| `fullyQualifiedPackageName` | Package name for code generation |

---

### 1.13 No Health Check Endpoint

**Problem**: No `/actuator/health` endpoint for service discovery.

**Impact**: Load balancers can't check service health.

---

### 1.14 Hardcoded Configuration

```properties
spring.datasource.password=hrishabh@123
```

**Problem**: Credentials in source code.

**Impact**: Security risk, can't use different values per environment.

---

## Part 2: New Architecture

### 2.1 Service Ecosystem

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ALGOCRACK ECOSYSTEM                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────┐      ┌─────────────────────┐                       │
│  │   PROBLEM SERVICE   │      │ SUBMISSION SERVICE  │                       │
│  │      (Port 8082)    │      │     (Port 8080)     │                       │
│  │                     │      │                     │                       │
│  │  - Question CRUD    │      │  - Submit code      │                       │
│  │  - TestCase CRUD    │      │  - Poll results     │                       │
│  │  - Solution CRUD    │      │  - WebSocket push   │                       │
│  │  - Tag CRUD         │      │  - User history     │                       │
│  │  - Metadata CRUD    │      │                     │                       │
│  │  - Search/Filter    │      │                     │                       │
│  │  - Pagination       │      │                     │                       │
│  └────────┬────────────┘      └──────────┬──────────┘                       │
│           │                              │                                   │
│           │    READS                     │    HTTP                           │
│           ▼                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐                │
│  │                    MySQL (leetcode)                      │                │
│  │  - questions, testcases, solutions, tags, metadata      │                │
│  │  - submissions, question_statistics                      │                │
│  └─────────────────────────────────────────────────────────┘                │
│                                          │                                   │
│                                          │    HTTP POST                      │
│                                          ▼                                   │
│                              ┌─────────────────────┐                        │
│                              │    CXE SERVICE      │                        │
│                              │    (Port 8081)      │                        │
│                              │                     │                        │
│                              │  - Execute code     │                        │
│                              │  - Redis queue      │                        │
│                              │  - Worker pool      │                        │
│                              └──────────┬──────────┘                        │
│                                         │                                    │
│                                         ▼                                    │
│                              ┌─────────────────────┐                        │
│                              │       Redis         │                        │
│                              │   (Job Queue)       │                        │
│                              └─────────────────────┘                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Service Responsibilities

| Service | Port | Responsibility | Actors |
|---------|------|----------------|--------|
| Problem Service | 8082 | Question CRUD, content management | Admins |
| Submission Service | 8080 | Code execution orchestration | End users |
| CXE Service | 8081 | Code execution engine | Internal |

### 2.3 Data Flow

```
                    ADMIN PANEL                      USER PLATFORM
                        │                                 │
                        ▼                                 ▼
                ┌───────────────┐               ┌───────────────┐
                │Problem Service│               │Submission Svc │
                │   (8082)      │               │   (8080)      │
                └───────┬───────┘               └───────┬───────┘
                        │                               │
                        │ WRITES                        │ READS
                        ▼                               ▼
              ┌────────────────────────────────────────────────┐
              │                    MySQL                        │
              │  questions, testcases, solutions, metadata     │
              └────────────────────────────────────────────────┘
```

---

## Part 3: Detailed Implementation Plan

### Phase 1: Configuration Updates

#### 1.1 Update Port and Configuration

**File**: `src/main/resources/application.yml` (replace application.properties)

```yaml
server:
  port: 8082

spring:
  application:
    name: AlgoCrack-ProblemService
  datasource:
    url: jdbc:mysql://localhost:3306/leetcode
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate  # Changed from update
    show-sql: false       # Disable in production
  flyway:
    enabled: true
    baseline-on-migrate: true

# Actuator for health checks
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when_authorized

logging:
  level:
    com.hrishabh.problemservice: DEBUG
```

#### 1.2 Update build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.4'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.hrishabh'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    
    compileOnly 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    runtimeOnly 'com.mysql:mysql-connector-j'
    
    annotationProcessor 'org.projectlombok:lombok'
    
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    
    // Updated Entity Service
    implementation 'com.hrishabh:AlgoCrack-EntityService:0.0.8-SNAPSHOT'
}
```

**Changes:**
- Added `spring-boot-starter-validation` for `@Valid` support
- Added `spring-boot-starter-actuator` for health checks
- Updated EntityService dependency

---

### Phase 2: Global Exception Handling

#### 2.1 Create GlobalExceptionHandler

**File**: `exceptions/GlobalExceptionHandler.java`

```java
package com.hrishabh.problemservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
```

---

### Phase 3: Add Missing APIs

#### 3.1 Add List Questions with Pagination

**File**: `controllers/ProblemsController.java` (add method)

```java
@GetMapping
public ResponseEntity<Page<QuestionSummaryDto>> listQuestions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String difficulty,
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) String search
) {
    Page<QuestionSummaryDto> questions = questionService.listQuestions(page, size, difficulty, tag, search);
    return ResponseEntity.ok(questions);
}
```

#### 3.2 Create QuestionSummaryDto

**File**: `dto/QuestionSummaryDto.java`

```java
package com.hrishabh.problemservice.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSummaryDto {
    private Long id;
    private String questionTitle;
    private String difficultyLevel;
    private List<String> tags;
    private String company;
    private Long acceptanceRate;  // From QuestionStatistics
    private Integer totalSubmissions;  // From QuestionStatistics
}
```

#### 3.3 Add Search Specification

**File**: `repository/QuestionSpecification.java`

```java
package com.hrishabh.problemservice.repository;

import com.hrishabh.algocrackentityservice.models.Question;
import org.springframework.data.jpa.domain.Specification;

public class QuestionSpecification {

    public static Specification<Question> hasDifficulty(String difficulty) {
        return (root, query, cb) -> 
            difficulty == null ? null : cb.equal(root.get("difficultyLevel"), difficulty);
    }

    public static Specification<Question> hasTag(String tagName) {
        return (root, query, cb) -> {
            if (tagName == null) return null;
            return cb.isMember(tagName, root.join("tags").get("name"));
        };
    }

    public static Specification<Question> titleContains(String search) {
        return (root, query, cb) -> 
            search == null ? null : cb.like(cb.lower(root.get("questionTitle")), "%" + search.toLowerCase() + "%");
    }
}
```

#### 3.4 Update QuestionsRepository

```java
public interface QuestionsRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {
    // Existing methods...
}
```

#### 3.5 Add to QuestionService

```java
public Page<QuestionSummaryDto> listQuestions(int page, int size, String difficulty, String tag, String search) {
    Specification<Question> spec = Specification.where(QuestionSpecification.hasDifficulty(difficulty))
            .and(QuestionSpecification.hasTag(tag))
            .and(QuestionSpecification.titleContains(search));

    Page<Question> questions = questionsRepository.findAll(spec, PageRequest.of(page, size));

    return questions.map(q -> QuestionSummaryDto.builder()
            .id(q.getId())
            .questionTitle(q.getQuestionTitle())
            .difficultyLevel(q.getDifficultyLevel())
            .tags(q.getTags().stream().map(Tag::getName).collect(Collectors.toList()))
            .company(q.getCompany())
            .build());
}
```

---

### Phase 4: Complete Missing CRUD Operations

#### 4.1 TestCase APIs

**File**: `controllers/TestcasesController.java` (update)

```java
@RestController
@RequestMapping("/api/v1/testcases")
@RequiredArgsConstructor
public class TestcasesController {

    private final TestcasesService testcasesService;

    @PostMapping
    public ResponseEntity<Void> addTestCase(@Valid @RequestBody TestCaseRequestDto dto) {
        testcasesService.addTestCase(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestCaseResponseDto> getTestCase(@PathVariable Long id) {
        return ResponseEntity.ok(testcasesService.getTestCase(id));
    }

    @GetMapping("/question/{questionId}")
    public ResponseEntity<List<TestCaseResponseDto>> getTestCasesByQuestion(@PathVariable Long questionId) {
        return ResponseEntity.ok(testcasesService.getTestCasesByQuestion(questionId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TestCaseResponseDto> updateTestCase(
            @PathVariable Long id,
            @Valid @RequestBody TestCaseRequestDto dto
    ) {
        return ResponseEntity.ok(testcasesService.updateTestCase(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long id) {
        testcasesService.deleteTestCase(id);
        return ResponseEntity.noContent().build();
    }
}
```

#### 4.2 Tag APIs

**File**: `controllers/TagController.java` (update)

```java
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagServiceImpl tagService;

    @PostMapping
    public ResponseEntity<Void> addTag(@Valid @RequestBody CreateTagRequestDto dto) {
        tagService.addTag(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<TagResponseDto>> listTags() {
        return ResponseEntity.ok(tagService.listTags());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponseDto> getTag(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getTag(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }
}
```

#### 4.3 Solution APIs

**File**: `controllers/SolutionsController.java` (update)

```java
@RestController
@RequestMapping("/api/v1/solutions")
@RequiredArgsConstructor
public class SolutionsController {

    private final SolutionService solutionService;

    @GetMapping("/question/{questionId}")
    public ResponseEntity<List<SolutionResponseDto>> getSolutionsByQuestion(@PathVariable Long questionId) {
        return ResponseEntity.ok(solutionService.getSolutionsByQuestion(questionId));
    }

    @PostMapping
    public ResponseEntity<SolutionResponseDto> addSolution(@Valid @RequestBody CreateSolutionRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solutionService.addSolution(dto));
    }

    @PutMapping("/{solutionId}")
    public ResponseEntity<SolutionResponseDto> updateSolution(
            @PathVariable Long solutionId,
            @Valid @RequestBody UpdateSolutionRequestDto dto
    ) {
        return ResponseEntity.ok(solutionService.updateSolution(solutionId, dto));
    }

    @DeleteMapping("/{solutionId}")
    public ResponseEntity<Void> deleteSolution(@PathVariable Long solutionId) {
        solutionService.deleteSolution(solutionId);
        return ResponseEntity.noContent().build();
    }
}
```

---

### Phase 5: Add Validation Annotations

#### 5.1 Update QuestionRequestDto

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequestDto {

    @NotBlank(message = "Question title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String questionTitle;

    @NotBlank(message = "Question description is required")
    private String questionDescription;

    @NotEmpty(message = "At least one test case is required")
    @Valid
    private List<TestCaseDto> testCases;

    @NotEmpty(message = "At least one metadata entry is required")
    @Valid
    private List<QuestionMetadataDto> metadataList;

    private Boolean isOutputOrderMatters;
    private List<TagDto> tags;
    
    @Pattern(regexp = "^(Easy|Medium|Hard)$", message = "Difficulty must be Easy, Medium, or Hard")
    private String difficultyLevel;
    
    private String company;
    private String constraints;
    
    @Min(value = 1, message = "Timeout must be at least 1 second")
    @Max(value = 30, message = "Timeout must not exceed 30 seconds")
    private Integer timeoutLimit;
    
    private List<SolutionDto> solution;
}
```

---

### Phase 6: Remove Unused Code

#### 6.1 Delete SubmissionsRepository

**File to DELETE**: `repository/SubmissionsRepository.java`

**Reason**: Submissions are managed by Submission Service only.

---

### Phase 7: Add Statistics Integration

#### 7.1 Create QuestionStatisticsRepository

(Only if Problem Service needs to display statistics)

```java
package com.hrishabh.problemservice.repository;

import com.hrishabh.algocrackentityservice.models.QuestionStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface QuestionStatisticsRepository extends JpaRepository<QuestionStatistics, Long> {
    Optional<QuestionStatistics> findByQuestionId(Long questionId);
}
```

#### 7.2 Enhance QuestionSummaryDto with Stats

```java
@Data
@Builder
public class QuestionSummaryDto {
    private Long id;
    private String questionTitle;
    private String difficultyLevel;
    private List<String> tags;
    private String company;
    
    // From QuestionStatistics
    private Double acceptanceRate;
    private Integer totalSubmissions;
    private Integer acceptedSubmissions;
}
```

---

## Part 4: Complete API Reference After Upgrade

### Questions API (`/api/v1/questions`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/questions` | Create question |
| `GET` | `/api/v1/questions` | List with pagination & filters |
| `GET` | `/api/v1/questions/{id}` | Get by ID |
| `PUT` | `/api/v1/questions/{id}` | Update question |
| `DELETE` | `/api/v1/questions/{id}` | Delete question |

### TestCases API (`/api/v1/testcases`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/testcases` | Add test case |
| `GET` | `/api/v1/testcases/{id}` | Get by ID |
| `GET` | `/api/v1/testcases/question/{questionId}` | Get by question |
| `PUT` | `/api/v1/testcases/{id}` | Update |
| `DELETE` | `/api/v1/testcases/{id}` | Delete |

### Solutions API (`/api/v1/solutions`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/solutions` | Add solution |
| `GET` | `/api/v1/solutions/question/{questionId}` | Get by question |
| `PUT` | `/api/v1/solutions/{id}` | Update |
| `DELETE` | `/api/v1/solutions/{id}` | Delete |

### Tags API (`/api/v1/tags`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/tags` | Create tag |
| `GET` | `/api/v1/tags` | List all tags |
| `GET` | `/api/v1/tags/{id}` | Get by ID |
| `DELETE` | `/api/v1/tags/{id}` | Delete tag |

---

## Part 5: Files Summary

### Files to CREATE

| File | Purpose |
|------|---------|
| `exceptions/GlobalExceptionHandler.java` | Centralized error handling |
| `dto/QuestionSummaryDto.java` | List response DTO |
| `dto/TagResponseDto.java` | Tag response DTO |
| `dto/TestCaseResponseDto.java` | TestCase response DTO |
| `dto/CreateSolutionRequestDto.java` | Solution creation DTO |
| `repository/QuestionSpecification.java` | Filtering support |
| `repository/QuestionStatisticsRepository.java` | Stats access |
| `src/main/resources/application.yml` | New config format |

### Files to MODIFY

| File | Changes |
|------|---------|
| `build.gradle` | Add validation, actuator, update EntityService |
| `controllers/ProblemsController.java` | Add list endpoint |
| `controllers/TestcasesController.java` | Add CRUD endpoints |
| `controllers/SolutionsController.java` | Add CRUD endpoints |
| `controllers/TagController.java` | Add list, get, delete |
| `service/QuestionService.java` | Add list with filters |
| `service/TestcasesService.java` | Add get, update, delete |
| `service/SolutionService.java` | Add get, create, delete |
| `service/TagServiceImpl.java` | Add list, get, delete |
| `repository/QuestionsRepository.java` | Extend JpaSpecificationExecutor |
| `dto/QuestionRequestDto.java` | Add validation annotations |

### Files to DELETE

| File | Reason |
|------|--------|
| `repository/SubmissionsRepository.java` | Belongs in Submission Service |
| `src/main/resources/application.properties` | Replaced by YAML |

---

## Part 6: Implementation Order

1. **Phase 1**: Configuration Updates (port, YAML, build.gradle)
2. **Phase 2**: Global Exception Handler
3. **Phase 3**: Add List Questions with Pagination & Search
4. **Phase 4**: Complete Missing CRUD (TestCases, Solutions, Tags)
5. **Phase 5**: Add Validation Annotations
6. **Phase 6**: Remove Unused Code
7. **Phase 7**: Statistics Integration (optional)
8. **Phase 8**: Testing & Verification

---

## Part 7: Verification

1. **Port Check**: Verify service starts on 8082
2. **Health Check**: `GET http://localhost:8082/actuator/health`
3. **List Questions**: `GET http://localhost:8082/api/v1/questions?page=0&size=10`
4. **Search**: `GET http://localhost:8082/api/v1/questions?search=two&difficulty=Easy`
5. **Error Handling**: Verify consistent JSON error format
