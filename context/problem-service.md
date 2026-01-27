# AlgoCrack Problem Service - Complete Context Documentation

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Dependencies](#dependencies)
- [Entity Models](#entity-models)
- [API Reference](#api-reference)
- [Controllers](#controllers)
- [Services](#services)
- [Repositories](#repositories)
- [DTOs](#dtos)
- [Helper Classes](#helper-classes)
- [Exceptions](#exceptions)
- [Logical Flow](#logical-flow)
- [Configuration](#configuration)

---

## Overview

**AlgoCrack Problem Service** is a Spring Boot microservice responsible for managing coding problems/questions in a LeetCode-like platform. This service handles the complete lifecycle of:

- **Questions/Problems**: Creating, reading, updating, and deleting coding problems
- **Test Cases**: Adding and validating test cases for problems
- **Solutions**: Managing solution code for problems
- **Tags**: Creating and associating category tags with problems
- **Metadata**: Managing language-specific function signatures and execution configurations

### Key Responsibilities:
1. CRUD operations for coding questions
2. Managing test cases with input/output validation
3. Handling solutions in multiple programming languages
4. Tag management for categorization
5. Storing metadata for code execution (function name, parameter types, return types)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         PROBLEM SERVICE                                  │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                         CONTROLLERS                               │   │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌──────────────────┐   │   │
│  │  │ ProblemsCtrl    │ │ SolutionsCtrl   │ │ TestcasesCtrl    │   │   │
│  │  │ /api/v1/questions│ │ /api/v1/solution│ │ /api/v1/testcases│   │   │
│  │  └────────┬────────┘ └────────┬────────┘ └────────┬─────────┘   │   │
│  │           │                   │                   │              │   │
│  │  ┌────────┴───────────────────┴───────────────────┴──────────┐  │   │
│  │  │                      TAG CONTROLLER                        │  │   │
│  │  │                    /api/v1/tags                            │  │   │
│  │  └───────────────────────────┬────────────────────────────────┘  │   │
│  └──────────────────────────────┼───────────────────────────────────┘   │
│                                 │                                        │
│  ┌──────────────────────────────┼───────────────────────────────────┐   │
│  │                         SERVICES                                  │   │
│  │  ┌─────────────────┐ ┌──────┴────────┐ ┌──────────────────┐      │   │
│  │  │ QuestionService │ │SolutionService│ │TestcasesService  │      │   │
│  │  └────────┬────────┘ └───────┬───────┘ └────────┬─────────┘      │   │
│  │           │                  │                   │                │   │
│  │  ┌────────┴──────────────────┴───────────────────┴────────────┐  │   │
│  │  │                    TagServiceImpl                           │  │   │
│  │  └───────────────────────────┬────────────────────────────────┘  │   │
│  └──────────────────────────────┼───────────────────────────────────┘   │
│                                 │                                        │
│  ┌──────────────────────────────┼───────────────────────────────────┐   │
│  │                        REPOSITORIES                               │   │
│  │  ┌───────────────┐ ┌─────────┴─────────┐ ┌───────────────────┐   │   │
│  │  │ Questions     │ │ Solutions         │ │ Testcases         │   │   │
│  │  │ Repository    │ │ Repository        │ │ Repository        │   │   │
│  │  └───────────────┘ └───────────────────┘ └───────────────────┘   │   │
│  │  ┌───────────────┐ ┌───────────────────┐ ┌───────────────────┐   │   │
│  │  │ Tag           │ │ QuestionMetadata  │ │ Submissions       │   │   │
│  │  │ Repository    │ │ Repository        │ │ Repository        │   │   │
│  │  └───────────────┘ └───────────────────┘ └───────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                 │                                        │
└─────────────────────────────────┼────────────────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────┐
                    │      MySQL Database     │
                    │    (leetcode schema)    │
                    └─────────────────────────┘
                                  │
                                  ▼
          ┌───────────────────────────────────────────────────┐
          │            AlgoCrack Entity Service               │
          │         (External Shared Entity Library)          │
          │                                                   │
          │  Entities: Question, TestCase, Solution,          │
          │            Tag, QuestionMetadata, Submission      │
          └───────────────────────────────────────────────────┘
```

---

## Dependencies

### build.gradle

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

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    compileOnly 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    runtimeOnly 'com.mysql:mysql-connector-j'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    implementation 'com.github.hrishabh6:AlgoCrack-EntityService:v1.0:plain'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

### Key Dependencies:
| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-data-jpa` | JPA/Hibernate for database operations |
| `spring-boot-starter-web` | REST API support |
| `lombok` | Boilerplate reduction (getters, setters, builders) |
| `mysql-connector-j` | MySQL database driver |
| `AlgoCrack-EntityService` | Shared entity models from JitPack |

---

## Entity Models

> **Note**: Entity models are imported from the external `AlgoCrack-EntityService` library located at `com.hrishabh.algocrackentityservice.models`.

### Entities Used:
| Entity | Description |
|--------|-------------|
| `Question` | Main problem entity with title, description, constraints, difficulty |
| `TestCase` | Input/output pairs for validating submissions |
| `Solution` | Reference solution code in various languages |
| `Tag` | Category labels (e.g., "Array", "Dynamic Programming") |
| `QuestionMetadata` | Language-specific function signatures and execution config |
| `Submission` | User code submissions (repository only, not actively used) |
| `Language` | Enum for supported programming languages |

---

## API Reference

### Base URL: `http://localhost:8080`

### Questions API (`/api/v1/questions`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/questions` | Create a new question | `QuestionRequestDto` | `CreateQuestionResponseDto` |
| `GET` | `/api/v1/questions/{id}` | Get question by ID | - | `QuestionResponseDto` |
| `PUT` | `/api/v1/questions/{id}` | Update question | `QuestionRequestDto` | `QuestionResponseDto` |
| `DELETE` | `/api/v1/questions/{id}` | Delete question | - | `String` message |

### Solutions API (`/api/v1/solution`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `PUT` | `/api/v1/solution/{solutionId}` | Update a solution | `UpdateSolutionRequestDto` | `SolutionResponseDto` |

### Testcases API (`/api/v1/testcases`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/testcases` | Add a test case | `TestCaseRequestDto` | `201 Created` |

### Tags API (`/api/v1/tags`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/tags` | Create a new tag | `CreateTagRequestDto` | `201 Created` |

---

## Controllers

### 1. ProblemsController

**File**: `controllers/ProblemsController.java`

**Base Path**: `/api/v1/questions`

```java
package com.hrishabh.problemservice.controllers;

import com.hrishabh.problemservice.dto.QuestionRequestDto;
import com.hrishabh.problemservice.dto.CreateQuestionResponseDto;
import com.hrishabh.problemservice.dto.QuestionResponseDto;
import com.hrishabh.problemservice.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
public class ProblemsController {

    private final QuestionService questionService;

    @Autowired
    public ProblemsController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<CreateQuestionResponseDto> createQuestion(
            @RequestBody QuestionRequestDto requestDto
    ) {
        return questionService.saveQuestion(requestDto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionResponseDto> getQuestionById(@PathVariable Long id) {
        QuestionResponseDto dto = questionService.getQuestionById(id);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteQuestion(@PathVariable Long id) {
        try {
            questionService.deleteQuestionById(id);
            return ResponseEntity.ok("Question deleted successfully.");
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionResponseDto> updateQuestion(
            @PathVariable Long id,
            @RequestBody QuestionRequestDto updateDto
    ) {
        QuestionResponseDto updatedQuestion = questionService.updateQuestion(id, updateDto);
        return ResponseEntity.ok(updatedQuestion);
    }
}
```

---

### 2. SolutionsController

**File**: `controllers/SolutionsController.java`

**Base Path**: `/api/v1/solution`

```java
package com.hrishabh.problemservice.controllers;

import com.hrishabh.problemservice.dto.SolutionResponseDto;
import com.hrishabh.problemservice.dto.UpdateSolutionRequestDto;
import com.hrishabh.problemservice.service.SolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/solution")
public class SolutionsController {

    private final SolutionService solutionService;

    public SolutionsController(SolutionService solutionService) {
        this.solutionService = solutionService;
    }

    @PutMapping("/{solutionId}")
    public ResponseEntity<SolutionResponseDto> updateSolution(
            @PathVariable Long solutionId,
            @RequestBody UpdateSolutionRequestDto dto
    ) {
        SolutionResponseDto updated = solutionService.updateSolution(solutionId, dto);
        return ResponseEntity.ok(updated);
    }
}
```

---

### 3. TagController

**File**: `controllers/TagController.java`

**Base Path**: `/api/v1/tags`

```java
package com.hrishabh.problemservice.controllers;

import com.hrishabh.problemservice.dto.CreateTagRequestDto;
import com.hrishabh.problemservice.service.TagServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {
    private final TagServiceImpl tagService;

    public TagController(TagServiceImpl tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    public ResponseEntity<Void> addTag(@RequestBody CreateTagRequestDto dto) {
        tagService.addTag(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build(); // 201 Created
    }
}
```

---

### 4. TestcasesController

**File**: `controllers/TestcasesController.java`

**Base Path**: `/api/v1/testcases`

```java
package com.hrishabh.problemservice.controllers;

import com.hrishabh.problemservice.dto.TestCaseRequestDto;
import com.hrishabh.problemservice.service.TestcasesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/testcases")
public class TestcasesController {

    private final TestcasesService testcasesService;

    public TestcasesController(TestcasesService testcasesService) {
        this.testcasesService = testcasesService;
    }

    @PostMapping
    public ResponseEntity<Void> addTestCase(@RequestBody TestCaseRequestDto dto) {
        testcasesService.addTestCase(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build(); // or .noContent().build() for 204
    }
}
```

---

## Services

### 1. QuestionService

**File**: `service/QuestionService.java`

The main service handling all question-related business logic.

```java
package com.hrishabh.problemservice.service;

import com.hrishabh.algocrackentityservice.models.*;
import com.hrishabh.problemservice.dto.*;
import com.hrishabh.problemservice.exceptions.ResourceNotFoundException;
import com.hrishabh.problemservice.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private final QuestionsRepository questionsRepository;
    private final TagRepository tagRepository;

    public QuestionService(QuestionsRepository questionsRepository,
                           TagRepository tagRepository) {
        this.questionsRepository = questionsRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public ResponseEntity<CreateQuestionResponseDto> saveQuestion(QuestionRequestDto dto) {

        // ✅ Validate required fields
        if (dto.getQuestionTitle() == null || dto.getQuestionTitle().isBlank()) {
            return ResponseEntity.badRequest().body(
                    CreateQuestionResponseDto.builder()
                            .message("Question title cannot be empty")
                            .build()
            );
        }

        if (dto.getTestCases() == null || dto.getTestCases().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    CreateQuestionResponseDto.builder()
                            .message("At least one test case is required")
                            .build()
            );
        }

        if (dto.getMetadataList() == null || dto.getMetadataList().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    CreateQuestionResponseDto.builder()
                            .message("At least one metadata entry is required")
                            .build()
            );
        }

        for (QuestionMetadataDto meta : dto.getMetadataList()) {
            if (meta.getFunctionName() == null || meta.getReturnType() == null ||
                    meta.getParamTypes() == null || meta.getParamNames() == null ||
                    meta.getParamTypes().size() != meta.getParamNames().size()) {
                return ResponseEntity.badRequest().body(
                        CreateQuestionResponseDto.builder()
                                .message("Invalid metadata: parameter names/types must be non-null and size must match")
                                .build()
                );
            }
        }

        // ✅ Validate tags exist in DB
        List<Tag> tags = new ArrayList<>();
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            for (TagDto tagDto : dto.getTags()) {
                Optional<Tag> existing = tagRepository.findByName(tagDto.getName());
                if (existing.isEmpty()) {
                    return ResponseEntity.badRequest().body(
                            CreateQuestionResponseDto.builder()
                                    .message("Tag does not exist: " + tagDto.getName())
                                    .build()
                    );
                }
                tags.add(existing.get());
            }
        }

        // ✅ Create Question
        Question question = Question.builder()
                .questionTitle(dto.getQuestionTitle())
                .questionDescription(dto.getQuestionDescription())
                .isOutputOrderMatters(dto.getIsOutputOrderMatters())
                .difficultyLevel(dto.getDifficultyLevel())
                .company(dto.getCompany())
                .constraints(dto.getConstraints())
                .timeoutLimit(dto.getTimeoutLimit())
                .tags(tags)
                .build();

        // ✅ Map and attach test cases
        List<TestCase> testCases = dto.getTestCases().stream().map(tc ->
                TestCase.builder()
                        .input(tc.getInput())
                        .expectedOutput(tc.getExpectedOutput())
                        .orderIndex(tc.getOrderIndex())
                        .isHidden(tc.getIsHidden())
                        .question(question)
                        .build()
        ).collect(Collectors.toList());

        question.setTestCases(testCases);

        // ✅ Map and attach solutions
        List<Solution> solutions = dto.getSolution() != null ? dto.getSolution().stream().map(sol ->
                Solution.builder()
                        .code(sol.getCode())
                        .language(sol.getLanguage())
                        .question(question)
                        .build()
        ).collect(Collectors.toList()) : new ArrayList<>();

        question.setSolutions(solutions);

        // ✅ Map and attach metadata
        List<QuestionMetadata> metadataList = dto.getMetadataList().stream().map(md ->
                QuestionMetadata.builder()
                        .functionName(md.getFunctionName())
                        .returnType(md.getReturnType())
                        .paramTypes(md.getParamTypes())
                        .paramNames(md.getParamNames())
                        .language(md.getLanguage())
                        .codeTemplate(md.getCodeTemplate())
                        .executionStrategy(md.getExecutionStrategy())
                        .customInputEnabled(md.getCustomInputEnabled())
                        .question(question)
                        .build()
        ).collect(Collectors.toList());

        question.setMetadataList(metadataList);

        // ✅ Save everything
        Question savedQuestion = questionsRepository.save(question);

        return ResponseEntity.ok(
                CreateQuestionResponseDto.builder()
                        .questionId(savedQuestion.getId())
                        .message("Question created successfully")
                        .build()
        );
    }


    public QuestionResponseDto getQuestionById(Long id) {
        Question question = questionsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found with ID: " + id));

        return QuestionResponseDto.builder()
                .id(question.getId())
                .questionTitle(question.getQuestionTitle())
                .questionDescription(question.getQuestionDescription())
                .isOutputOrderMatters(question.getIsOutputOrderMatters())
                .tags(question.getTags()
                        .stream()
                        .map(Tag::getName)
                        .collect(Collectors.toList()))
                .difficultyLevel(question.getDifficultyLevel())
                .company(question.getCompany())
                .constraints(question.getConstraints())
                .build();
    }

    public void deleteQuestionById(Long id) {
        if (!questionsRepository.existsById(id)) {
            throw new RuntimeException("Question not found with ID: " + id);
        }
        questionsRepository.deleteById(id);
    }

    public QuestionResponseDto updateQuestion(Long id, QuestionRequestDto dto) {
        Question question = questionsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + id));

        // Update only if field is not null
        if (dto.getQuestionTitle() != null) {
            question.setQuestionTitle(dto.getQuestionTitle());
        }

        if (dto.getQuestionDescription() != null) {
            question.setQuestionDescription(dto.getQuestionDescription());
        }

        if (dto.getIsOutputOrderMatters() != null) {
            question.setIsOutputOrderMatters(dto.getIsOutputOrderMatters());
        }

        if (dto.getDifficultyLevel() != null) {
            question.setDifficultyLevel(dto.getDifficultyLevel());
        }

        if (dto.getCompany() != null) {
            question.setCompany(dto.getCompany());
        }

        if (dto.getConstraints() != null) {
            question.setConstraints(dto.getConstraints());
        }

        if (dto.getTimeoutLimit() != null) {
            question.setTimeoutLimit(dto.getTimeoutLimit());
        }

        // Optional: Handle tags (if tag updating is allowed)
        if (dto.getTags() != null) {
            // Fetch all existing tags in one query
            List<String> tagNames = dto.getTags().stream()
                    .map(TagDto::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.toList());

            List<Tag> existingTags = tagRepository.findByNameIn(tagNames);
            Map<String, Tag> tagMap = existingTags.stream()
                    .collect(Collectors.toMap(Tag::getName, tag -> tag));

            // Create and save new tags in bulk
            List<Tag> newTags = tagNames.stream()
                    .filter(name -> !tagMap.containsKey(name))
                    .map(name -> Tag.builder().name(name).build())
                    .collect(Collectors.toList());

            if (!newTags.isEmpty()) {
                tagRepository.saveAll(newTags);
                newTags.forEach(tag -> tagMap.put(tag.getName(), tag));
            }

            List<Tag> updatedTags = tagNames.stream()
                    .map(tagMap::get)
                    .collect(Collectors.toList());

            question.setTags(updatedTags);
        }

        questionsRepository.save(question);

        // Reuse the same mapper as getQuestionById
        return QuestionResponseDto.builder()
                .id(question.getId())
                .questionTitle(question.getQuestionTitle())
                .questionDescription(question.getQuestionDescription())
                .isOutputOrderMatters(question.getIsOutputOrderMatters())
                .tags(question.getTags().stream().map(Tag::getName).collect(Collectors.toList()))
                .difficultyLevel(question.getDifficultyLevel())
                .company(question.getCompany())
                .constraints(question.getConstraints())
                .build();
    }
}
```

#### QuestionService Methods Summary:

| Method | Description |
|--------|-------------|
| `saveQuestion(QuestionRequestDto)` | Creates a new question with test cases, solutions, metadata, and tags |
| `getQuestionById(Long)` | Retrieves a question by ID |
| `deleteQuestionById(Long)` | Deletes a question by ID |
| `updateQuestion(Long, QuestionRequestDto)` | Updates existing question fields (null-safe partial update) |

---

### 2. SolutionService

**File**: `service/SolutionService.java`

```java
package com.hrishabh.problemservice.service;

import com.hrishabh.algocrackentityservice.models.Solution;
import com.hrishabh.problemservice.dto.SolutionResponseDto;
import com.hrishabh.problemservice.dto.UpdateSolutionRequestDto;
import com.hrishabh.problemservice.exceptions.ResourceNotFoundException;
import com.hrishabh.problemservice.repository.SolutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SolutionService {

    private final SolutionRepository solutionRepository;

    public SolutionService(SolutionRepository solutionRepository) {
        this.solutionRepository = solutionRepository;
    }

    public SolutionResponseDto updateSolution(Long solutionId, UpdateSolutionRequestDto dto) {
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found with ID: " + solutionId));

        if (dto.getCode() != null) {
            solution.setCode(dto.getCode());
        }

        if (dto.getLanguage() != null) {
            solution.setLanguage(dto.getLanguage());
        }

        Solution updated = solutionRepository.save(solution);

        return SolutionResponseDto.builder()
                .id(updated.getId())
                .code(updated.getCode())
                .language(updated.getLanguage())
                .questionId(updated.getQuestion().getId())
                .build();
    }
}
```

---

### 3. TagServiceImpl

**File**: `service/TagServiceImpl.java`

```java
package com.hrishabh.problemservice.service;

import com.hrishabh.algocrackentityservice.models.Tag;
import com.hrishabh.problemservice.dto.CreateTagRequestDto;
import com.hrishabh.problemservice.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TagServiceImpl {

    private final TagRepository tagRepository;

    public void addTag(CreateTagRequestDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }

        if (tagRepository.findByName(dto.getName()).isPresent()) {
            throw new IllegalArgumentException("Tag with name '" + dto.getName() + "' already exists");
        }

        Tag tag = Tag.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .build();

        tagRepository.save(tag);
    }
}
```

---

### 4. TestcasesService

**File**: `service/TestcasesService.java`

```java
package com.hrishabh.problemservice.service;

import com.hrishabh.algocrackentityservice.models.Question;
import com.hrishabh.algocrackentityservice.models.QuestionMetadata;
import com.hrishabh.algocrackentityservice.models.TestCase;
import com.hrishabh.problemservice.dto.TestCaseRequestDto;
import com.hrishabh.problemservice.exceptions.ResourceNotFoundException;
import com.hrishabh.problemservice.helper.Validation;
import com.hrishabh.problemservice.repository.QuestionsRepository;
import com.hrishabh.problemservice.repository.TestcasesRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestcasesService {

    private final QuestionsRepository questionsRepository;
    private final TestcasesRepository testcasesRepository;
    private final Validation validation = new Validation();

    public TestcasesService(QuestionsRepository questionsRepository, TestcasesRepository testcasesRepository) {
        this.questionsRepository = questionsRepository;
        this.testcasesRepository = testcasesRepository;
    }

    public void addTestCase(TestCaseRequestDto dto) {
        Question question = questionsRepository.findById(dto.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id " + dto.getQuestionId()));

        // 🔍 Get latest metadata (optional: or by preferred language)
        if (question.getMetadataList().isEmpty()) {
            throw new IllegalArgumentException("No metadata found for the question.");
        }

        QuestionMetadata metadata = question.getMetadataList().get(0); // or filter by language

        // 🔍 Validate input and expectedOutput against metadata
        validation.validateTestCaseInputAndOutput(dto.getInput(), dto.getExpectedOutput(), metadata);

        // ✅ Save after validation
        TestCase testCase = TestCase.builder()
                .question(question)
                .input(dto.getInput())
                .expectedOutput(dto.getExpectedOutput())
                .orderIndex(dto.getOrderIndex())
                .isHidden(dto.getIsHidden())
                .build();

        testcasesRepository.save(testCase);

        // Later, you want to get test cases for that question
        Question savedQuestion = questionsRepository.findById(question.getId()).get();
        List<TestCase> testCases = savedQuestion.getTestCases(); // ✅ Will give test cases

        for (TestCase tc : testCases) {
            System.out.println("----- Test Case -----");
            System.out.println(tc.getId() + " " + tc.getInput() + " " + tc.getExpectedOutput());
        }
    }
}
```

---

## Repositories

All repositories extend `JpaRepository` from Spring Data JPA, providing standard CRUD operations.

### 1. QuestionsRepository

```java
package com.hrishabh.problemservice.repository;

import com.hrishabh.algocrackentityservice.models.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionsRepository extends JpaRepository<Question, Long>{
}
```

### 2. QuestionMetadataRepository

```java
package com.hrishabh.problemservice.repository;

import com.hrishabh.algocrackentityservice.models.QuestionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionMetadataRepository extends JpaRepository<QuestionMetadata, Long> {
}
```

### 3. SolutionRepository

```java
package com.hrishabh.problemservice.repository;

import com.hrishabh.algocrackentityservice.models.Solution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionRepository extends JpaRepository<Solution, Long> {
}
```

### 4. SubmissionsRepository

```java
package com.hrishabh.problemservice.repository;

import com.hrishabh.algocrackentityservice.models.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionsRepository extends JpaRepository<Submission, Long> {
}
```

### 5. TagRepository

```java
package com.hrishabh.problemservice.repository;

import com.hrishabh.algocrackentityservice.models.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    List<Tag> findByNameIn(Collection<String> names);
}
```

### 6. TestcasesRepository

```java
package com.hrishabh.problemservice.repository;

import com.hrishabh.algocrackentityservice.models.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestcasesRepository extends JpaRepository<TestCase, Long> {
}
```

---

## DTOs

### 1. CreateQuestionResponseDto

**Purpose**: Response returned after creating a question.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionResponseDto {
    private Long questionId;
    private String message;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `questionId` | `Long` | ID of the created question |
| `message` | `String` | Success/error message |

---

### 2. CreateTagRequestDto

**Purpose**: Request body for creating a new tag.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTagRequestDto {
    private String name;
    private String description;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Tag name (e.g., "Array", "DP") |
| `description` | `String` | Optional tag description |

---

### 3. QuestionMetadataDto

**Purpose**: Metadata for a question's function signature and execution config.

```java
package com.hrishabh.problemservice.dto;

import com.hrishabh.algocrackentityservice.models.Language;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionMetadataDto {
    private String functionName;
    private String returnType;
    private List<String> paramTypes;
    private List<String> paramNames;
    private Language language;
    private String codeTemplate;
    private String executionStrategy;
    private Boolean customInputEnabled;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `functionName` | `String` | Name of the function to be implemented (e.g., "twoSum") |
| `returnType` | `String` | Return type (e.g., "List<int>", "int", "string") |
| `paramTypes` | `List<String>` | List of parameter types |
| `paramNames` | `List<String>` | List of parameter names |
| `language` | `Language` | Programming language enum |
| `codeTemplate` | `String` | Starter code template |
| `executionStrategy` | `String` | Execution strategy identifier |
| `customInputEnabled` | `Boolean` | Whether custom input is allowed |

---

### 4. QuestionRequestDto

**Purpose**: Request body for creating or updating a question.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequestDto {

    private String questionTitle;
    private String questionDescription;
    private List<TestCaseDto> testCases;
    private List<QuestionMetadataDto> metadataList;
    private Boolean isOutputOrderMatters;
    private List<TagDto> tags;
    private String difficultyLevel;
    private String company;
    private String constraints;
    private Integer timeoutLimit;
    private List<SolutionDto> solution;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `questionTitle` | `String` | Title of the problem |
| `questionDescription` | `String` | Full problem description |
| `testCases` | `List<TestCaseDto>` | List of test cases |
| `metadataList` | `List<QuestionMetadataDto>` | Language-specific metadata |
| `isOutputOrderMatters` | `Boolean` | Whether output order matters in comparison |
| `tags` | `List<TagDto>` | Associated tags |
| `difficultyLevel` | `String` | "Easy", "Medium", "Hard" |
| `company` | `String` | Associated company name |
| `constraints` | `String` | Problem constraints text |
| `timeoutLimit` | `Integer` | Execution timeout in seconds |
| `solution` | `List<SolutionDto>` | Reference solutions |

---

### 5. QuestionResponseDto

**Purpose**: Response returned when fetching a question.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponseDto {
    private Long id;
    private String questionTitle;
    private String questionDescription;
    private Boolean isOutputOrderMatters;
    private List<String> tags;
    private String difficultyLevel;
    private String company;
    private String constraints;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Question ID |
| `questionTitle` | `String` | Problem title |
| `questionDescription` | `String` | Problem description |
| `isOutputOrderMatters` | `Boolean` | Output order flag |
| `tags` | `List<String>` | Tag names (not full TagDto) |
| `difficultyLevel` | `String` | Difficulty level |
| `company` | `String` | Associated company |
| `constraints` | `String` | Problem constraints |

---

### 6. SolutionDto

**Purpose**: Solution data for creating a question.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolutionDto {
    private String code;
    private String language;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `code` | `String` | Solution source code |
| `language` | `String` | Programming language |

---

### 7. SolutionResponseDto

**Purpose**: Response returned when fetching/updating a solution.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolutionResponseDto {
    private Long id;
    private String code;
    private String language;
    private String explanation;
    private Long questionId;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Solution ID |
| `code` | `String` | Solution source code |
| `language` | `String` | Programming language |
| `explanation` | `String` | Optional explanation |
| `questionId` | `Long` | Associated question ID |

---

### 8. TagDto

**Purpose**: Tag data for associating with questions.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDto {
    private String name;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Tag name |

---

### 9. TestCaseDto

**Purpose**: Test case data for creating a question.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDto {
    private String input;
    private String expectedOutput;
    private Integer orderIndex;
    private Boolean isHidden;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `input` | `String` | JSON array of input parameters |
| `expectedOutput` | `String` | Expected output value |
| `orderIndex` | `Integer` | Order/sequence number |
| `isHidden` | `Boolean` | Whether hidden from users |

---

### 10. TestCaseRequestDto

**Purpose**: Request body for adding a test case to an existing question.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseRequestDto {
    private Long questionId;
    private String input;
    private String expectedOutput;
    private Integer orderIndex;
    private Boolean isHidden;
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `questionId` | `Long` | ID of the question to add test case to |
| `input` | `String` | JSON array of input parameters |
| `expectedOutput` | `String` | Expected output value |
| `orderIndex` | `Integer` | Order/sequence number |
| `isHidden` | `Boolean` | Whether hidden from users |

---

### 11. UpdateSolutionRequestDto

**Purpose**: Request body for updating an existing solution.

```java
package com.hrishabh.problemservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSolutionRequestDto {
    private String code;
    private String language;
    private String explanation; // optional, if applicable
}
```

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `code` | `String` | Updated solution code |
| `language` | `String` | Programming language |
| `explanation` | `String` | Optional explanation |

---

## Helper Classes

### Validation

**File**: `helper/Validation.java`

**Purpose**: Validates test case input/output against question metadata (function signature).

```java
package com.hrishabh.problemservice.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrishabh.algocrackentityservice.models.QuestionMetadata;

import java.util.List;

public class Validation {

    private boolean isTypeCompatible(String expectedType, JsonNode node) {
        switch (expectedType.toLowerCase()) {
            case "int":
            case "integer":
                return node.isInt() || node.isLong();
            case "double":
            case "float":
                return node.isDouble() || node.isFloat() || node.isInt(); // allow 5.0 to match double
            case "string":
                return node.isTextual();
            case "boolean":
                return node.isBoolean();
            case "list<int>":
            case "list<string>":
            case "list<boolean>":
                return node.isArray(); // basic check, not full element-level type
            default:
                return true; // allow unhandled types
        }
    }

    public void validateTestCaseInputAndOutput(String inputJson, String outputJson, QuestionMetadata metadata) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            // Parse input JSON string to array
            JsonNode inputNode = mapper.readTree(inputJson);

            if (!inputNode.isArray()) {
                throw new IllegalArgumentException("Input must be a JSON array");
            }

            List<String> paramTypes = metadata.getParamTypes();
            if (inputNode.size() != paramTypes.size()) {
                throw new IllegalArgumentException("Input parameter count does not match metadata");
            }

            for (int i = 0; i < paramTypes.size(); i++) {
                String expectedType = paramTypes.get(i);
                JsonNode actual = inputNode.get(i);

                if (!isTypeCompatible(expectedType, actual)) {
                    throw new IllegalArgumentException("Type mismatch at param index " + i + ": expected " + expectedType + ", got " + actual);
                }
            }

            // Validate expected output
            JsonNode outputNode = mapper.readTree(outputJson);
            String expectedReturnType = metadata.getReturnType();

            if (!isTypeCompatible(expectedReturnType, outputNode)) {
                throw new IllegalArgumentException("Return type mismatch. Expected: " + expectedReturnType);
            }

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON in input/output", e);
        }
    }
}
```

#### Validation Features:
- Parses JSON input/output
- Validates parameter count matches metadata
- Type-checks each parameter against expected types
- Validates return type matches expected return type
- Supports: `int`, `integer`, `double`, `float`, `string`, `boolean`, `list<int>`, `list<string>`, `list<boolean>`

---

## Exceptions

### ResourceNotFoundException

**File**: `exceptions/ResourceNotFoundException.java`

```java
package com.hrishabh.problemservice.exceptions;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

**Purpose**: Thrown when a requested resource (Question, Solution, etc.) is not found in the database.

---

## Logical Flow

### 1. Create Question Flow

```
Client Request                        ProblemsController
     │                                      │
     ├──── POST /api/v1/questions ─────────►│
     │     (QuestionRequestDto)             │
     │                                      ▼
     │                               QuestionService.saveQuestion()
     │                                      │
     │      ┌───────────────────────────────┤
     │      │ 1. Validate title not empty   │
     │      │ 2. Validate testCases exist   │
     │      │ 3. Validate metadata exists   │
     │      │ 4. Validate param names/types │
     │      │ 5. Validate tags exist in DB  │
     │      └───────────────────────────────┤
     │                                      │
     │         If validation fails:         │
     │◄─── Return 400 Bad Request ─────────┤
     │                                      │
     │         If validation passes:        │
     │                                      ▼
     │                            Build Question entity
     │                                      │
     │                            Map TestCases → attach to Question
     │                            Map Solutions → attach to Question
     │                            Map Metadata → attach to Question
     │                            Set Tags → attach to Question
     │                                      │
     │                                      ▼
     │                            questionsRepository.save(question)
     │                            (Cascades save to related entities)
     │                                      │
     │                                      ▼
     │◄───── 200 OK ──────────────────────┤
     │      CreateQuestionResponseDto      │
     │      { questionId, message }        │
     ▼                                      ▼
```

### 2. Get Question Flow

```
Client Request                        ProblemsController
     │                                      │
     ├──── GET /api/v1/questions/{id} ─────►│
     │                                      │
     │                                      ▼
     │                            QuestionService.getQuestionById()
     │                                      │
     │                                      ▼
     │                            questionsRepository.findById(id)
     │                                      │
     │         If not found:                │
     │◄─── Throw RuntimeException ─────────┤
     │                                      │
     │         If found:                    │
     │                                      ▼
     │                            Map Question → QuestionResponseDto
     │                            (Extract tag names to List<String>)
     │                                      │
     │◄───── 200 OK ──────────────────────┤
     │      QuestionResponseDto            │
     ▼                                      ▼
```

### 3. Add Test Case Flow

```
Client Request                    TestcasesController
     │                                    │
     ├──── POST /api/v1/testcases ───────►│
     │     (TestCaseRequestDto)           │
     │                                    ▼
     │                           TestcasesService.addTestCase()
     │                                    │
     │                                    ▼
     │                           questionsRepository.findById(questionId)
     │                                    │
     │      If question not found:        │
     │◄─── ResourceNotFoundException ────┤
     │                                    │
     │                                    ▼
     │                           Get first QuestionMetadata
     │                                    │
     │      If no metadata:               │
     │◄─── IllegalArgumentException ─────┤
     │                                    │
     │                                    ▼
     │                           Validation.validateTestCaseInputAndOutput()
     │                                    │
     │      ┌─────────────────────────────┤
     │      │ 1. Parse input JSON         │
     │      │ 2. Verify input is array    │
     │      │ 3. Check param count        │
     │      │ 4. Type-check each param    │
     │      │ 5. Parse output JSON        │
     │      │ 6. Type-check return value  │
     │      └─────────────────────────────┤
     │                                    │
     │      If validation fails:          │
     │◄─── IllegalArgumentException ─────┤
     │                                    │
     │      If validation passes:         │
     │                                    ▼
     │                           Build TestCase entity
     │                           testcasesRepository.save(testCase)
     │                                    │
     │◄───── 201 Created ────────────────┤
     ▼                                    ▼
```

### 4. Add Tag Flow

```
Client Request                        TagController
     │                                      │
     ├──── POST /api/v1/tags ──────────────►│
     │     (CreateTagRequestDto)            │
     │                                      ▼
     │                             TagServiceImpl.addTag()
     │                                      │
     │      If name is null/blank:          │
     │◄─── IllegalArgumentException ───────┤
     │                                      │
     │      If tag already exists:          │
     │◄─── IllegalArgumentException ───────┤
     │                                      │
     │      Otherwise:                      │
     │                                      ▼
     │                             Build Tag entity
     │                             tagRepository.save(tag)
     │                                      │
     │◄───── 201 Created ─────────────────┤
     ▼                                      ▼
```

### 5. Update Solution Flow

```
Client Request                     SolutionsController
     │                                     │
     ├── PUT /api/v1/solution/{id} ───────►│
     │   (UpdateSolutionRequestDto)        │
     │                                     ▼
     │                           SolutionService.updateSolution()
     │                                     │
     │                                     ▼
     │                           solutionRepository.findById(id)
     │                                     │
     │      If not found:                  │
     │◄─── ResourceNotFoundException ─────┤
     │                                     │
     │      If found:                      │
     │                                     ▼
     │                           Update code (if not null)
     │                           Update language (if not null)
     │                                     │
     │                                     ▼
     │                           solutionRepository.save(solution)
     │                                     │
     │◄───── 200 OK ─────────────────────┤
     │      SolutionResponseDto           │
     ▼                                     ▼
```

---

## Configuration

### application.properties

```properties
spring.application.name=ProblemService
spring.datasource.username=root
spring.datasource.password=hrishabh@123
spring.datasource.url=jdbc:mysql://localhost:3306/leetcode
spring.jpa.show-sql=true
server.port=8080
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=update
```

| Property | Value | Description |
|----------|-------|-------------|
| `spring.application.name` | `ProblemService` | Application name |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/leetcode` | MySQL connection URL |
| `spring.datasource.username` | `root` | Database username |
| `spring.datasource.password` | `hrishabh@123` | Database password |
| `spring.jpa.show-sql` | `true` | Log SQL queries |
| `server.port` | `8080` | Server port |
| `spring.flyway.enabled` | `false` | Flyway migrations disabled |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-update schema on startup |

### Main Application Class

```java
package com.hrishabh.problemservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.hrishabh.algocrackentityservice.models")
@EnableJpaRepositories("com.hrishabh.problemservice.repository")
@EnableJpaAuditing
public class ProblemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProblemServiceApplication.class, args);
    }
}
```

**Key Annotations**:
- `@SpringBootApplication`: Main Spring Boot application
- `@EntityScan("com.hrishabh.algocrackentityservice.models")`: Scan for entities in external library
- `@EnableJpaRepositories("com.hrishabh.problemservice.repository")`: Enable JPA repositories
- `@EnableJpaAuditing`: Enable auditing features (e.g., `@CreatedDate`, `@LastModifiedDate`)

---

## Project Structure

```
AlgoCrack-ProblemService/
├── build.gradle
├── Dockerfile
├── settings.gradle
├── src/
│   └── main/
│       ├── java/
│       │   └── com/hrishabh/problemservice/
│       │       ├── ProblemServiceApplication.java
│       │       ├── controllers/
│       │       │   ├── ProblemsController.java
│       │       │   ├── SolutionsController.java
│       │       │   ├── TagController.java
│       │       │   └── TestcasesController.java
│       │       ├── dto/
│       │       │   ├── CreateQuestionResponseDto.java
│       │       │   ├── CreateTagRequestDto.java
│       │       │   ├── QuestionMetadataDto.java
│       │       │   ├── QuestionRequestDto.java
│       │       │   ├── QuestionResponseDto.java
│       │       │   ├── SolutionDto.java
│       │       │   ├── SolutionResponseDto.java
│       │       │   ├── TagDto.java
│       │       │   ├── TestCaseDto.java
│       │       │   ├── TestCaseRequestDto.java
│       │       │   └── UpdateSolutionRequestDto.java
│       │       ├── exceptions/
│       │       │   └── ResourceNotFoundException.java
│       │       ├── helper/
│       │       │   └── Validation.java
│       │       ├── repository/
│       │       │   ├── QuestionMetadataRepository.java
│       │       │   ├── QuestionsRepository.java
│       │       │   ├── SolutionRepository.java
│       │       │   ├── SubmissionsRepository.java
│       │       │   ├── TagRepository.java
│       │       │   └── TestcasesRepository.java
│       │       └── service/
│       │           ├── QuestionService.java
│       │           ├── SolutionService.java
│       │           ├── TagServiceImpl.java
│       │           └── TestcasesService.java
│       └── resources/
│           └── application.properties
└── context/
    └── problem-service.md (this file)
```

---

## Summary

The **AlgoCrack Problem Service** is a comprehensive microservice for managing coding problems with the following capabilities:

1. **Question Management**: Full CRUD with support for test cases, solutions, tags, and metadata
2. **Validation**: Type-safe validation of test case inputs/outputs against function signatures
3. **Tag System**: Category tagging with uniqueness enforcement
4. **Multi-language Support**: Metadata per language with code templates
5. **Shared Entities**: Uses external entity library for consistent data models across microservices

The service is built with Spring Boot 3.5.4, uses MySQL for persistence, and follows a clean layered architecture (Controller → Service → Repository).
