# Judging Pipeline — Remaining Edge Cases

## 1. Floating Point Comparator
**Affects:** Problems returning `double`/`float` (e.g., Median of Two Sorted Arrays)
**Problem:** `ExactMatch` does string comparison — `"2.0"` ≠ `"2.00000"` would fail.
**Fix:**
- [ ] Create `FloatingPointComparator` with configurable epsilon (default 1e-5)
- [ ] Add routing in `PipelineAssembler.selectComparator()`: if `returnType` is `double`/`float` → use it
- **Effort:** ~30 lines, 1 new file + 1 routing rule

---

## 2. ~~Class-Based Execution Strategy (CXE)~~ ✅ DONE
**Status:** CXE `JavaDesignClassGenerator` implemented. Entity Service + Submission Service + DB wired.
- [x] CXE: `JavaDesignClassGenerator`, routing, `JavaSolutionClassGenerator`, `JavaFileGenerator`, `JavaCompilationService`
- [x] Entity Service: `questionType` on `QuestionMetadata`
- [x] Submission Service: `questionType` flows through `CodeBundle` → `CxeExecutionAdapter` → `ExecutionRequest`
- [x] Database: `question_type` column added, Q4 set to `DESIGN_CLASS`
- [ ] End-to-end test with Codec question (test case format must be `[[ops],[args]]`)

---

## 3. Whitespace-Sensitive String Comparison
**Affects:** Text Justification, problems where exact spacing matters
**Problem:** `ExactMatch` trims whitespace by default.
**Fix:**
- [ ] Add `WHITESPACE_SENSITIVE` validation hint
- [ ] Create `WhitespaceStrictComparator` or modify ExactMatch to skip trimming when hint present
- **Effort:** ~20 lines
- **Priority:** Very low — rarely needed

---

## 4. Multiple Valid Answers (Different Structure)
**Affects:** Problems where multiple structurally different outputs are all correct
**Example:** Some path-finding problems, topological sort (multiple valid orderings)
**Current coverage:** `isOutputOrderMatters = false` handles most cases (unordered lists).
**Remaining gap:** If the oracle produces `[1,2,3]` but `[3,1,2]` is also valid AND order matters — this fails.
**Fix:**
- [ ] For topological sort: add a `TopologicalOrderValidator` that verifies dependency ordering
- [ ] For other cases: evaluate per-problem whether a validator can check the property
- **Priority:** Low — very few problems need this

---

## Notes
- Items 1 and 2 cover ~5% of LeetCode problems
- Items 3 and 4 cover <1%
- Current pipeline handles ~95% of problem types with zero code changes
