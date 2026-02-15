# Advanced Judging Strategy — Implementation Plan v2

## Problem Statement

The current judging system performs **simple string/JSON equality** comparison between user output and oracle output. This only works for ~10% of LeetCode-style questions. The remaining 90% require specialized judging involving unordered comparison, void return handling, node type serialization, structural validation, and more.

---

## Architectural Foundation (Unchanged)

These decisions from v1 are **correct and non-negotiable**:

- **All judging logic lives in the Submission Service.** CXE is a pure execution engine.
- **Metadata-driven routing** using `returnType`, `nodeType`, `isOutputOrderMatters`, `executionStrategy`.
- **Strategy pattern** as the abstraction for judging — but now evolved into a composable pipeline.

---

## v1 Flaws Addressed in v2

| v1 Flaw | v2 Fix |
|---|---|
| Strategy explosion (9 separate classes encoding problem categories) | **Composable pipeline phases** — strategies are assembled from reusable components |
| Resolver becomes god object | **Resolver assembles pipelines from phases**, not picks monolithic strategies |
| `judge(String, String, context)` interface too narrow | **Richer interface** taking `ExecutionResult` with access to outputs, errors, metadata |
| Void-return handling underspecified | **Formalized contract** with `mutationTarget` + `serializationStrategy` in metadata |
| Direct `QuestionRepository` coupling | **`JudgingMetadataDto`** fetched via metadata service, no direct entity access |

---

## Core Architecture: Composable Judging Pipeline

### The Pipeline

Instead of one monolithic `JudgingStrategy` per problem type, every judgment is a **4-phase pipeline** assembled from reusable, independent components:

```
┌─────────────────────────────────────────────────────────────┐
│                    JudgingPipeline                           │
│                                                             │
│  Phase 1: OutputExtractor                                   │
│  ┌─────────────────────────────────────────────────┐        │
│  │ Extract/transform raw output into judgeable form │        │
│  │ (identity, tree serialize, void mutation read)   │        │
│  └─────────────────────────────────────────────────┘        │
│                          │                                  │
│  Phase 2: OutputNormalizer                                  │
│  ┌─────────────────────────────────────────────────┐        │
│  │ Normalize for fair comparison                    │        │
│  │ (trim, sort lists, normalize edges, canonical)   │        │
│  └─────────────────────────────────────────────────┘        │
│                          │                                  │
│  Phase 3: OutputComparator                                  │
│  ┌─────────────────────────────────────────────────┐        │
│  │ Compare normalized user vs oracle output         │        │
│  │ (exact match, set equality, structural equality) │        │
│  └─────────────────────────────────────────────────┘        │
│                          │                                  │
│  Phase 4: OutputValidator (optional)                        │
│  ┌─────────────────────────────────────────────────┐        │
│  │ Verify structural constraints beyond equality    │        │
│  │ (sudoku rules, tree structure, no cycles)        │        │
│  └─────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### Why This Is Better Than v1

```
v1: 10 questions → 9 different Strategy classes → combinatorial explosion
v2: 10 questions → ~4 extractors, ~3 normalizers, ~3 comparators, ~3 validators → mix & match
```

New problem types = pick existing components + maybe add one new one. No new "mega-strategy."

---

## Phase Interfaces

### `OutputExtractor`

Transforms raw CXE string output into a structured form suitable for comparison.

```java
public interface OutputExtractor {
    /**
     * Extract judgeable output from raw execution result.
     * For standard returns: identity (pass-through)
     * For void returns: extract serialized mutation from stdout
     * For node types: parse serialized structure
     */
    Object extract(String rawOutput, JudgingContext context);
}
```

**Implementations:**

| Extractor | What It Does | Used When |
|---|---|---|
| `IdentityExtractor` | Pass-through, returns raw string | Default for primitives |
| `JsonArrayExtractor` | Parse string as JSON array | returnType contains `List` |
| `JsonObjectExtractor` | Parse string as JSON object | returnType is complex object |

### `OutputNormalizer`

Normalizes extracted output so that semantically equivalent outputs compare equal.

```java
public interface OutputNormalizer {
    /**
     * Normalize output for fair comparison.
     * e.g., sort lists, normalize edge direction, canonical tree form
     */
    Object normalize(Object extracted, JudgingContext context);
}
```

**Implementations:**

| Normalizer | What It Does | Used When |
|---|---|---|
| `IdentityNormalizer` | No-op, pass-through | Order matters, simple types |
| `SortedListNormalizer` | Sort outer list elements | `isOutputOrderMatters == false`, simple list |
| `SortedNestedListNormalizer` | Sort inner lists, then sort outer list | `isOutputOrderMatters == false`, nested lists (e.g., 4Sum) |
| `EdgeNormalizer` | Normalize edge `[a,b]` → `[min,max]`, then sort | `isOutputOrderMatters == false`, edges (e.g., Critical Connections) |

### `OutputComparator`

Compares two normalized outputs.

```java
public interface OutputComparator {
    /**
     * Compare normalized user output vs normalized oracle output.
     * @return ComparisonResult with passed/failed + details
     */
    ComparisonResult compare(Object userNormalized, Object oracleNormalized, JudgingContext context);
}
```

**Implementations:**

| Comparator | What It Does | Used When |
|---|---|---|
| `ExactMatchComparator` | `string.equals(string)` | Default |
| `JsonDeepComparator` | Jackson `JsonNode.equals()` | JSON-parseable outputs |
| `SetEqualityComparator` | Unordered set comparison | `isOutputOrderMatters == false` |
| `StructuralTreeComparator` | Recursive tree structure match | nodeType == TREE_NODE |

### `OutputValidator` (Optional)

Extra validation beyond output equality. Validators declare an **execution stage** to control when they run:

```java
public interface OutputValidator {
    /**
     * When should this validator run relative to comparison?
     */
    default ValidationStage getStage() {
        return ValidationStage.POST_COMPARE;  // default: after comparison passes
    }
    
    /**
     * Validate structural constraints on user output.
     */
    ValidationResult validate(Object userOutput, JudgingContext context);
}

public enum ValidationStage {
    PRE_COMPARE,   // Run BEFORE comparison (e.g., structural constraints that must hold regardless)
    POST_COMPARE   // Run AFTER comparison passes (default — most validators live here)
}
```

**Why two stages?** Most validators only matter after outputs match (POST_COMPARE). But some problems (Sudoku, graph clone) need structural validation even if the outputs look equal — a shallow copy might "look equal" but violate reference independence. PRE_COMPARE handles these cases without complicating the 90% default path.

**Implementations:**

| Validator | Stage | What It Does | Used When |
|---|---|---|---|
| `SudokuConstraintValidator` | `POST_COMPARE` | Row/col/box uniqueness | Sudoku Solver |
| `FlattenedTreeValidator` | `POST_COMPARE` | All left == null, right forms linked list | Flatten Binary Tree |
| `NoCycleValidator` | `PRE_COMPARE` | Detect cycles in linked list output | Reorder List |

---

## `JudgingPipeline` — The Orchestrator

```java
@Builder
public class JudgingPipeline {
    private final OutputExtractor extractor;
    private final OutputNormalizer normalizer;
    private final OutputComparator comparator;
    private final List<OutputValidator> validators;  // optional, can be empty
    
    public JudgingResult judge(ExecutionOutput userOutput, ExecutionOutput oracleOutput, 
                               JudgingContext context) {
        
        // Guard: Oracle failure should never penalize the user
        if (oracleOutput.hasError()) {
            return JudgingResult.judgeError(
                "Oracle execution failed: " + oracleOutput.getError());
        }
        
        // Phase 1: Extract
        Object userExtracted = extractor.extract(userOutput.getRawOutput(), context);
        Object oracleExtracted = extractor.extract(oracleOutput.getRawOutput(), context);
        
        // Phase 2: PRE_COMPARE validators (structural constraints that must hold regardless)
        for (OutputValidator validator : validators) {
            if (validator.getStage() == ValidationStage.PRE_COMPARE) {
                ValidationResult validation = validator.validate(userExtracted, context);
                if (!validation.isPassed()) {
                    return JudgingResult.failed(validation.getReason(),
                            userExtracted.toString(), oracleExtracted.toString());
                }
            }
        }
        
        // Phase 3: Normalize
        Object userNormalized = normalizer.normalize(userExtracted, context);
        Object oracleNormalized = normalizer.normalize(oracleExtracted, context);
        
        // Phase 4: Compare
        ComparisonResult comparison = comparator.compare(userNormalized, oracleNormalized, context);
        if (!comparison.isPassed()) {
            return JudgingResult.failed(comparison.getReason(), 
                    userNormalized.toString(), oracleNormalized.toString());
        }
        
        // Phase 5: POST_COMPARE validators (constraints verified after equality confirmed)
        for (OutputValidator validator : validators) {
            if (validator.getStage() == ValidationStage.POST_COMPARE) {
                ValidationResult validation = validator.validate(userExtracted, context);
                if (!validation.isPassed()) {
                    return JudgingResult.failed(validation.getReason(),
                            userNormalized.toString(), oracleNormalized.toString());
                }
            }
        }
        
        return JudgingResult.passed(userNormalized.toString(), oracleNormalized.toString());
    }
}
```

> [!NOTE]
> The pipeline now has 5 internal steps but still 4 logical phases. Oracle guard runs unconditionally. PRE_COMPARE validators run before normalization/comparison. POST_COMPARE validators confirm structural rules after equality. This preserves the invariant: **the user is never penalized for oracle failure.**

---

## `JudgingContext` — Richer Than v1

```java
@Data
@Builder
public class JudgingContext {
    private String returnType;           // "int", "void", "ListNode", "List<List<Integer>>"
    private NodeType nodeType;           // TREE_NODE, GRAPH_NODE, LIST_NODE, null
    private Boolean isOutputOrderMatters;
    private String executionStrategy;    // "function", "class"
    private Long questionId;
    
    // v2 additions for void-return formalization
    private String mutationTarget;       // "input[0]" — which param is mutated
    private String serializationStrategy; // "LEVEL_ORDER", "PREORDER", "ARRAY", etc.
}
```

---

## `JudgingResult` and Supporting DTOs

```java
@Data
@Builder
public class JudgingResult {
    private boolean passed;
    private String normalizedUserOutput;    // For frontend display
    private String normalizedOracleOutput;  // For frontend display
    private String failureReason;           // Why it failed (optional)
    
    public static JudgingResult passed(String userDisplay, String oracleDisplay) { ... }
    public static JudgingResult failed(String reason, String userDisplay, String oracleDisplay) { ... }
}

/**
 * Wraps raw execution output with metadata.
 * Richer than just a String — gives strategies access to errors, timing, etc.
 * Future-proofed with fields that may not be used immediately.
 */
@Data
@Builder
public class ExecutionOutput {
    private String rawOutput;
    private String error;
    private Long executionTimeMs;
    
    // Future-safe fields (add now, use later — avoids breaking interfaces)
    private boolean timedOut;       // Distinguishes timeout vs runtime error
    private Long memoryKb;          // Memory usage tracking
    private String errorType;       // "RUNTIME", "COMPILATION", "TIMEOUT", "OOM" — finer signal
    
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }
}
```

---

## `PipelineAssembler` — Replaces the Monolithic Resolver

Instead of a god-object resolver, the assembler **composes a pipeline from independent rules**:

```java
@Service
@RequiredArgsConstructor
public class PipelineAssembler {

    // All phase implementations injected as beans
    private final IdentityExtractor identityExtractor;
    private final JsonArrayExtractor jsonArrayExtractor;
    // ... etc
    
    public JudgingPipeline assemble(JudgingContext context) {
        return JudgingPipeline.builder()
                .extractor(selectExtractor(context))
                .normalizer(selectNormalizer(context))
                .comparator(selectComparator(context))
                .validators(selectValidators(context))
                .build();
    }
    
    private OutputExtractor selectExtractor(JudgingContext ctx) {
        // Each selection is independent — no combinatorial coupling
        if (ctx.getReturnType() != null && ctx.getReturnType().contains("List")) {
            return jsonArrayExtractor;
        }
        return identityExtractor;
    }
    
    private OutputNormalizer selectNormalizer(JudgingContext ctx) {
        if (Boolean.FALSE.equals(ctx.getIsOutputOrderMatters())) {
            if (ctx.getNodeType() == NodeType.GRAPH_NODE) {
                return edgeNormalizer;
            }
            if (ctx.getReturnType() != null && ctx.getReturnType().contains("List<List")) {
                return sortedNestedListNormalizer;
            }
            return sortedListNormalizer;
        }
        return identityNormalizer;
    }
    
    private OutputComparator selectComparator(JudgingContext ctx) {
        if (Boolean.FALSE.equals(ctx.getIsOutputOrderMatters())) {
            return setEqualityComparator;
        }
        return jsonDeepComparator;  // Handles both simple and complex types
    }
    
    private List<OutputValidator> selectValidators(JudgingContext ctx) {
        // Validators are additive — each rule adds if relevant
        List<OutputValidator> validators = new ArrayList<>();
        // Add problem-specific validators as needed
        return validators;
    }
}
```

**Why this doesn't become a god object:**
- Each `select*()` method is **independent** — normalizer selection doesn't affect extractor selection
- Adding a new normalizer doesn't require changing comparator logic
- Each method stays small and testable in isolation

---

## How Each Question Maps to a Pipeline

| # | Question | Extractor | Normalizer | Comparator | Validator |
|---|---|---|---|---|---|
| 1 | Trapping Rain Water II | `Identity` | `Identity` | `ExactMatch` | — |
| 2 | Reverse Nodes in k-Group | `JsonArray` | `Identity` | `JsonDeep` | — |
| 3 | Critical Connections | `JsonArray` | `Edge` | `SetEquality` | — |
| 4 | Serialize/Deserialize Tree | `JsonArray` | `Identity` | `StructuralTree` | — |
| 5 | Word Break II | `JsonArray` | `SortedList` | `SetEquality` | — |
| 6 | Flatten Binary Tree | `JsonArray` | `Identity` | `JsonDeep` | `FlattenedTree` |
| 7 | Reorder List | `JsonArray` | `Identity` | `JsonDeep` | `NoCycle` |
| 8 | Clone Graph | `JsonObject` | `Identity` | `StructuralTree`* | — |
| 9 | Sudoku Solver | `JsonArray` | `Identity` | `JsonDeep` | `SudokuConstraint` |
| 10 | 4Sum | `JsonArray` | `SortedNestedList` | `SetEquality` | — |

**Notice:** 10 questions use only **3 extractors, 4 normalizers, 3 comparators, 3 validators** = 13 components instead of 9 monolithic strategies. And every new question just picks from the existing components.

---

## Void Return Handling — Formalized Contract

> [!IMPORTANT]
> The single hardest judging challenge is **void return types** where the function mutates its input in-place, and CXE normally returns no useful output.

### The Problem

```
User code:  void flatten(TreeNode root) { ... }
CXE output: "" (empty — nothing was returned)
```

### The Solution: CXE Prints Modified Input for Void Functions

CXE's code generation already:
1. Constructs input data structures from JSON
2. Calls the user's function
3. Prints the return value

For void functions, step 3 simply **prints the mutated input** instead of the return value.

**This requires a small CXE change** — the code generator checks if `returnType == "void"` and prints the mutated input parameter instead of the return value.

### Metadata Contract for Void Returns

To make this unambiguous, `QuestionMetadata` needs two new conceptual fields that tell both CXE and the Submission Service what to serialize:

```
mutationTarget:        "input[0]"           // which parameter is mutated
serializationStrategy: "LEVEL_ORDER"        // how to serialize the result
```

**Where to store this:** These can be added as new fields on `QuestionMetadata` in the Entity Service, or encoded inside the existing `testCaseFormat` JSON field.

> [!WARNING]
> **CXE team action required:** CXE needs to handle `returnType == "void"` by serializing the parameter indicated by `mutationTarget` using the format specified by `serializationStrategy`, and printing it to stdout. This is a small, well-scoped change to CXE's `ResultPrinterGenerator` (or equivalent).

### Example Flow for Flatten Binary Tree (void + TREE_NODE)

```
1. Submission Service sends to CXE:
   - code: user's flatten() function
   - metadata.returnType: "void"
   - metadata.mutationTarget: "input[0]"
   - metadata.serializationStrategy: "LEVEL_ORDER"

2. CXE generates wrapper that:
   - Constructs TreeNode from input JSON
   - Calls user's flatten(root)
   - Serializes root as level-order array: [1,null,2,null,3,null,4,null,5,null,6]
   - Prints to stdout

3. Submission Service receives: "[1,null,2,null,3,null,4,null,5,null,6]"
   - Same pipeline as any other question
   - Extractor: JsonArrayExtractor
   - Normalizer: IdentityNormalizer
   - Comparator: JsonDeepComparator
   - Validator: FlattenedTreeValidator (verifies all left==null)
```

**Once CXE serializes the mutated input, void problems are no different from regular problems in the pipeline.** This is the key insight.

---

## Decoupled Metadata Access — `JudgingMetadataDto`

### Problem (v1)

Fetching `Question` entity directly via `QuestionRepository` couples judging to persistence.

### Solution (v2)

Create a lightweight DTO that carries all judging-relevant metadata, fetched once and passed through:

```java
@Data
@Builder
public class JudgingMetadataDto {
    private String returnType;
    private NodeType nodeType;
    private Boolean isOutputOrderMatters;
    private String executionStrategy;
    private String mutationTarget;
    private String serializationStrategy;
}
```

This is assembled from existing data we already fetch:

```java
// In UnifiedExecutionService — using data already available
JudgingMetadataDto judgingMeta = JudgingMetadataDto.builder()
    .returnType(metadata.getReturnType())     // from QuestionMetadata (already fetched)
    .nodeType(question.getNodeType())         // from Question (need to fetch)
    .isOutputOrderMatters(question.getIsOutputOrderMatters())
    .executionStrategy(metadata.getExecutionStrategy())
    .build();
```

**For fetching `Question`:** Rather than adding a `JpaRepository<Question>`, add a simple method to the existing metadata fetch that includes question-level fields. Or add a `QuestionMetadataRepository` method that joins to Question. This avoids a new repository for a single field fetch.

---

## Modifications to Existing Files

### [MODIFY] `UnifiedExecutionService.java`

```diff
 // Before (current):
 boolean passed = validationService.outputsMatch(userOutput.getOutput(), expectedOutput);

 // After (v2):
+JudgingContext context = buildJudgingContext(metadata, question);
+JudgingPipeline pipeline = pipelineAssembler.assemble(context);
+
+ExecutionOutput userExecOutput = ExecutionOutput.builder()
+    .rawOutput(userOutput.getOutput())
+    .error(userOutput.getError())
+    .executionTimeMs(userOutput.getExecutionTimeMs())
+    .build();
+
+ExecutionOutput oracleExecOutput = ExecutionOutput.builder()
+    .rawOutput(expectedOutput)
+    .build();
+
+JudgingResult result = pipeline.judge(userExecOutput, oracleExecOutput, context);
+boolean passed = result.isPassed();
```

Key changes:
- Inject `PipelineAssembler` as dependency
- Build `JudgingContext` from `QuestionMetadata` + `Question` entity
- Pipeline is assembled **once per question** (outside the per-testcase loop), not per testcase

### [MODIFY] `ResultValidationService.java`

- Keep existing `outputsMatch()` — it becomes the implementation inside `ExactMatchComparator`
- The `validateResults()` method used by the `/submit` flow also gets refactored to use the pipeline

---

## New File Inventory

### Package: `judging/`

| File | Type | Purpose |
|---|---|---|
| `JudgingPipeline.java` | Class | Orchestrates the 4-phase pipeline |
| `JudgingContext.java` | DTO | Metadata context for judging |
| `JudgingResult.java` | DTO | Result of judging one testcase |
| `ExecutionOutput.java` | DTO | Wraps raw output with metadata |
| `PipelineAssembler.java` | Service | Assembles pipeline from phases based on metadata |
| `JudgingMetadataDto.java` | DTO | Decoupled judging metadata (no entity dependency) |

### Package: `judging/extractor/`

| File | Purpose |
|---|---|
| `OutputExtractor.java` | Interface |
| `IdentityExtractor.java` | Pass-through (default) |
| `JsonArrayExtractor.java` | Parse as JSON array |
| `JsonObjectExtractor.java` | Parse as JSON object |

### Package: `judging/normalizer/`

| File | Purpose |
|---|---|
| `OutputNormalizer.java` | Interface |
| `IdentityNormalizer.java` | No-op (default) |
| `SortedListNormalizer.java` | Sort list elements |
| `SortedNestedListNormalizer.java` | Sort inner lists, then outer |
| `EdgeNormalizer.java` | Normalize `[a,b]` → `[min,max]`, then sort |

### Package: `judging/comparator/`

| File | Purpose |
|---|---|
| `OutputComparator.java` | Interface |
| `ComparisonResult.java` | Result DTO |
| `ExactMatchComparator.java` | String equality |
| `JsonDeepComparator.java` | Jackson JsonNode equality |
| `SetEqualityComparator.java` | Unordered set comparison |
| `StructuralTreeComparator.java` | Recursive tree structure match |

### Package: `judging/util/`

| File | Purpose |
|---|---|
| `TreeTraversalUtil.java` | Shared tree traversal/serialization utilities |
| `GraphTraversalUtil.java` | Shared graph traversal with cycle detection |
| `ListTraversalUtil.java` | Shared linked list traversal with cycle detection |

> [!TIP]
> Comparators should **compare**, not traverse deeply themselves. These utility classes keep traversal logic reusable across extractors, comparators, and validators that deal with the same data structures.

### Package: `judging/validator/`

| File | Purpose |
|---|---|
| `OutputValidator.java` | Interface (with `ValidationStage` enum) |
| `ValidationResult.java` | Result DTO |
| `ValidationStage.java` | Enum: `PRE_COMPARE`, `POST_COMPARE` |
| `SudokuConstraintValidator.java` | Row/col/box uniqueness (POST_COMPARE) |
| `FlattenedTreeValidator.java` | All left==null, right forms list (POST_COMPARE) |
| `NoCycleValidator.java` | Detect cycles in list output (PRE_COMPARE) |

### Modified Files

| File | Changes |
|---|---|
| `UnifiedExecutionService.java` | Use `PipelineAssembler` instead of `outputsMatch()` |
| `ResultValidationService.java` | Extract `ExactMatchComparator` from existing logic |

### Entity Service (Minimal)

| File | Changes |
|---|---|
| `QuestionMetadata.java` | Add `mutationTarget` and `serializationStrategy` fields (for void-return contract) |

---

## Implementation Phases

### Phase 1: Foundation (Zero Risk)
1. Create all interfaces: `OutputExtractor`, `OutputNormalizer`, `OutputComparator`, `OutputValidator`
2. Create DTOs: `JudgingPipeline`, `JudgingContext`, `JudgingResult`, `ExecutionOutput`, `ComparisonResult`, `ValidationResult`
3. Create `IdentityExtractor`, `IdentityNormalizer`, `ExactMatchComparator` (replicates current behavior)
4. Create `PipelineAssembler` — initially assembles the "identity" pipeline for everything
5. Wire into `UnifiedExecutionService`
6. **Verify:** Zero behavior change. Exact same judging as before.

### Phase 2: Unordered Comparison (Medium Risk)
7. Create `JsonArrayExtractor`
8. Create `SortedListNormalizer`, `SortedNestedListNormalizer`, `EdgeNormalizer`
9. Create `SetEqualityComparator`, `JsonDeepComparator`
10. Update `PipelineAssembler` to select these based on `isOutputOrderMatters` and `returnType`
11. Fetch `Question.isOutputOrderMatters` and `Question.nodeType` — add to metadata flow
12. **Verify:** Questions #3, #5, #10 judge correctly with unordered comparison

### Phase 3: Node Type Support (Low Risk)
13. Create `StructuralTreeComparator`
14. Update assembler to use structural comparison for node type outputs
15. **Verify:** Question #2 (LinkedList return) judges correctly

### Phase 4: Validators (Low Risk)
16. Create `SudokuConstraintValidator`, `FlattenedTreeValidator`, `NoCycleValidator`
17. Update assembler to attach validators based on question characteristics
18. **Verify:** Structural validation works for questions that need it

### Phase 5: Void Return Support (Requires CXE Coordination)
19. Add `mutationTarget`, `serializationStrategy` to `QuestionMetadata` entity
20. Coordinate with CXE team: void function code generation prints serialized mutated input
21. Once CXE supports void serialization, void problems flow through the same pipeline
22. **Verify:** Questions #6, #7, #9 judge correctly

### Phase 6: Special Cases
23. Handle `executionStrategy: "class"` for serialize/deserialize (Question #4)
24. Handle graph clone structural verification (Question #8)
25. **Verify:** All 10 questions judge correctly

---

## Key Decisions Summary

| Decision | Choice | Rationale |
|---|---|---|
| Judging architecture | Composable pipeline (not monolithic strategies) | Avoids combinatorial explosion, components are reusable |
| Where judging lives | Submission Service only | CXE is a pure execution engine |
| Void return handling | CXE serializes mutated input | Clean contract, void problems become regular pipeline problems |
| Metadata access | `JudgingMetadataDto` (decoupled) | No direct entity coupling in judging logic |
| Entity changes | Minimal — 2 new fields on `QuestionMetadata` | `mutationTarget`, `serializationStrategy` for void contract |
| CXE changes | Small — void return serialization | One change in code generation, well-scoped |
