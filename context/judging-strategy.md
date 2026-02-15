# Advanced Judging Strategy Documentation

## Overview

This document explains the **10 hard LeetCode-style questions** added to the system and the **specific judging strategies** each requires beyond the standard "run user code → run oracle code → compare outputs" model.

---

## Standard Judging Model (Baseline)

**Current Flow:**
```
1. Execute user's submitted code with test input
2. Execute oracle/reference solution with same input
3. Compare user output vs oracle output
4. If outputs match → ACCEPTED
```

**This works when:**
- Return type is a simple value (int, string, boolean)
- Output order matters and is deterministic
- No state mutation is involved

---

## Question Categories & Required Judging Modifications

---

## 📁 **FILE 1: `leetcode_hard_questions.json`**

Contains 4 questions with standard + custom node type judging.

---

### **Question 1: Trapping Rain Water II**

**Category:** Standard Array Problem  
**Return Type:** `int`  
**Node Type:** `null`  
**isOutputOrderMatters:** `false`

#### Judging Strategy
✅ **Standard judging works fine**

**Why:** Returns a single integer. No special handling needed.

**Judging Approach:**
- Direct equality comparison of integer outputs
- No special considerations

---

### **Question 2: Reverse Nodes in k-Group**

**Category:** LinkedList Manipulation  
**Return Type:** `ListNode`  
**Node Type:** `LIST_NODE`  
**isOutputOrderMatters:** `true`

#### Judging Strategy
⚠️ **Requires LinkedList traversal & comparison**

**Why:** User returns a `ListNode` reference (memory address). Can't compare object references directly.

**Judging Approach:**
- Convert both user's and oracle's LinkedLists to comparable format (e.g., array of values)
- Traverse node-by-node from returned head reference
- Compare the serialized representations

**Key Considerations:**
1. **Cycle Detection:**
   - User's buggy code might create circular references
   - Track visited nodes to prevent infinite loops during traversal
   
2. **Reference vs Value:**
   - Can't compare `user_node == oracle_node` (different memory addresses)
   - Must compare node values and structure

3. **Order Matters:**
   - LinkedList has strict sequential order
   - `[1,2,3,4]` ≠ `[4,3,2,1]`

4. **Edge Cases:**
   - Empty list (null head)
   - Single node
   - List shorter than k
   - k = 1 (no reversal needed)

---

### **Question 3: Critical Connections in a Network**

**Category:** Graph - Bridge Finding  
**Return Type:** `List<List<Integer>>`  
**Node Type:** `GRAPH_NODE`  
**isOutputOrderMatters:** `false`

#### Judging Strategy
⚠️ **Requires unordered collection comparison**

**Why:** The problem asks for "all critical connections in **any order**". Output is a list of edges `[[1,3], [2,5]]` but order doesn't matter.

**Judging Approach:**
- Normalize edge representations (both `[1,3]` and `[3,1]` represent same edge)
- Use set-based comparison to ignore order
- Ensure no duplicate edges in result

**Key Considerations:**
1. **Order Doesn't Matter:**
   - `[[1,3], [2,5]]` is identical to `[[2,5], [1,3]]`
   - `isOutputOrderMatters: false` flag indicates this
   
2. **Edge Normalization:**
   - Undirected edge: `[1,3]` equals `[3,1]`
   - Need to normalize before comparison (e.g., sort edge endpoints)
   
3. **Uniqueness:**
   - Result should have no duplicate edges
   - Set-based comparison naturally handles this

4. **Edge Cases:**
   - Graph with no bridges (empty result)
   - Tree (all edges are bridges)
   - Disconnected graph components

---

### **Question 4: Serialize and Deserialize Binary Tree**

**Category:** Tree Design Problem  
**Return Type:** `void` (Codec class with two methods)  
**Node Type:** `TREE_NODE`  
**isOutputOrderMatters:** `true`

#### Judging Strategy
🔴 **Requires round-trip verification + structural tree comparison**

**Why:** This is a **design problem**. User implements both `serialize()` and `deserialize()`. Judge must verify that `deserialize(serialize(root))` reconstructs the original tree exactly.

**Judging Approach:**
- Test round-trip correctness: original tree → serialize → deserialize → should equal original
- Compare tree structure recursively (not string representations)
- User's serialization format can differ from oracle's

**Key Considerations:**
1. **Format Independence:**
   - User can use **any** serialization format (preorder, level-order, custom encoding)
   - Oracle might use a completely different format
   - Don't compare serialized strings directly
   
2. **Round-Trip Verification:**
   - Must test: `deserialize(serialize(tree)) == tree` (structurally)
   - The serialized format itself doesn't matter, only correctness
   
3. **Tree Structural Equality:**
   - Can't compare tree object references
   - Must recursively verify:
     - Same node values
     - Same left/right child structure
     - Null children in same positions
   
4. **Edge Cases:**
   - Empty tree (null root)
   - Single node tree
   - Skewed tree (only left or only right children)
   - Complete binary tree
   - Tree with negative values

5. **What NOT to check:**
   - Don't enforce specific serialization format
   - Don't compare serialized strings
   - User's format just needs to work with their own deserialize method

---

## 📁 **FILE 2: `leetcode_hard_questions_advanced.json`**

Contains 6 questions with void return types and advanced judging.

---

### **Question 5: Word Break II**

**Category:** Backtracking with Multiple Solutions  
**Return Type:** `List<String>`  
**Node Type:** `null`  
**isOutputOrderMatters:** `false`

#### Judging Strategy
⚠️ **Requires unordered collection comparison**

**Why:** Problem asks for "all possible sentences in **any order**". Output like `["cat sand dog", "cats and dog"]` — list order doesn't matter.

**Judging Approach:**
- Convert both results to sets for comparison
- Ignore list ordering
- Each sentence itself has fixed word order (that order matters)

**Key Considerations:**
1. **Two Levels of Order:**
   - **List order doesn't matter**: `["A", "B"]` equals `["B", "A"]`
   - **Sentence word order matters**: `"cat sand dog"` ≠ `"dog sand cat"`
   
2. **Uniqueness:**
   - Each sentence should appear exactly once
   - Set comparison naturally enforces this
   
3. **Edge Cases:**
   - No valid word breaks (empty result)
   - Multiple ways to break same string
   - Single character words
   - Overlapping word choices

---

### **Question 6: Flatten Binary Tree to Linked List**

**Category:** In-place Tree Modification  
**Return Type:** `void`  
**Node Type:** `TREE_NODE`  
**isOutputOrderMatters:** `true`

#### Judging Strategy
🔴 **CRITICAL: Void return type — must inspect modified tree structure**

**Why:** User modifies the tree **in-place**. Function returns nothing (`void`). Judge must examine the modified tree by traversing from the original root reference.

**Judging Approach:**
- Function returns nothing, so can't compare return values
- After execution, traverse the tree structure that was passed as input
- Verify the tree has been flattened correctly (right-only linked list)
- Compare against oracle's modified tree

**Key Considerations:**
1. **THE CRITICAL PROBLEM:**
   - Standard judging: `user_result == oracle_result` 
   - Both return `None/void` → `None == None` is always true ❌
   - **Must inspect the modified input structure instead**

2. **Input Mutation:**
   - Function modifies the passed tree reference
   - After execution, traverse from the **same root** that was passed in
   - Need **separate copies** for user and oracle (can't use same tree)

3. **Deep Copy Requirement:**
   - Before execution: Make deep copy of original tree
   - Pass copy to user code, different copy to oracle code
   - After execution: Compare the two modified copies

4. **Verification Points:**
   - All `left` pointers should be `null`
   - `right` pointers form a linked list
   - Preorder traversal of original tree = flattened list order

5. **Edge Cases:**
   - Empty tree (null root)
   - Single node
   - Left-skewed tree
   - Right-skewed tree (already flattened)
   - Balanced tree

**Critical Implementation Notes:**
- Can't reuse the same tree for both user and oracle
- Each needs independent copy of the original input
- After modification, validate structure constraints (no left children)

---

### **Question 7: Reorder List**

**Category:** In-place LinkedList Modification  
**Return Type:** `void`  
**Node Type:** `LIST_NODE`  
**isOutputOrderMatters:** `true`

#### Judging Strategy
🔴 **CRITICAL: Void return type — must inspect modified LinkedList**

**Why:** User modifies the LinkedList **in-place**. Function returns nothing. Judge must traverse the modified list from the head reference.

**Judging Approach:**
- Function returns void
- After execution, traverse from the original head reference
- Convert to array for comparison
- Compare user's modified list vs oracle's

**Key Considerations:**
1. **Same Core Problem as Question 6:**
   - Can't compare return values (both void)
   - Must inspect the modified data structure
   - Need separate copies for user and oracle

2. **Deep Copy Requirement:**
   - Create independent copy of original LinkedList for user
   - Create another independent copy for oracle
   - After execution, both copies are modified
   - Compare the two modified lists

3. **Cycle Detection:**
   - User's buggy code might create circular references
   - Detect cycles during traversal to prevent infinite loops
   - Track visited nodes by memory address

4. **Verification Strategy:**
   - Traverse modified list node-by-node
   - Convert to array representation
   - Compare arrays for equality

5. **Edge Cases:**
   - Single node
   - Two nodes
   - Odd length list
   - Even length list
   - Very long list

**Why Deep Copy is Critical:**
```
❌ WRONG:
  head = build_list([1,2,3,4])
  user_code(head)    // Modifies head to [1,4,2,3]
  oracle_code(head)  // Operates on already-modified list! Wrong!

✅ CORRECT:
  user_head = deep_copy(original)
  oracle_head = deep_copy(original)
  user_code(user_head)      // Modifies user's copy
  oracle_code(oracle_head)  // Modifies oracle's copy
  compare(user_head, oracle_head)
```

---

### **Question 8: Clone Graph**

**Category:** Graph Deep Copy  
**Return Type:** `Node`  
**Node Type:** `GRAPH_NODE`  
**isOutputOrderMatters:** `false`

#### Judging Strategy
🔴 **CRITICAL: Must verify deep copy correctness + graph isomorphism**

**Why:** User must return a **completely new graph** with no shared references to the original. Simply comparing node values isn't enough.

**Judging Approach:**
- Verify no shared object references between original and clone
- Verify structural equality (same connections)
- Check graph isomorphism (same structure, possibly different traversal order)

**Key Considerations:**
1. **Deep Copy Verification:**
   - Clone must have **zero shared references** with original
   - Every node in clone must be a new object
   - `original_node is clone_node` should be false for ALL nodes
   
2. **Structural Equality:**
   - Same node values
   - Same neighbor connections
   - Same graph topology
   
3. **Reference Independence:**
   - Modifying clone should NOT affect original
   - Modifying original should NOT affect clone
   
4. **Graph Traversal:**
   - Can't assume nodes are returned in same order
   - Must perform graph traversal to verify structure
   - Track visited nodes to avoid infinite loops (cycles are valid)

5. **What to Verify:**
   - ✅ All nodes cloned (none missing)
   - ✅ All edges preserved
   - ✅ No shared references
   - ✅ Correct neighbor relationships
   - ❌ Don't check neighbor ordering (order doesn't matter)

6. **Edge Cases:**
   - Single node with self-loop
   - Disconnected components
   - Complete graph (all nodes connected)
   - Empty graph (null input)

**The Challenge:**
- Can't do simple equality: `user_clone == oracle_clone` (different objects)
- Must verify both structural equivalence AND reference independence
- Need graph traversal algorithm to check isomorphism

---

### **Question 9: Sudoku Solver**

**Category:** In-place 2D Array Modification  
**Return Type:** `void`  
**Node Type:** `null`  
**isOutputOrderMatters:** `true`

#### Judging Strategy
🔴 **CRITICAL: Void return + must validate Sudoku constraints**

**Why:** User modifies the board **in-place**. Function returns nothing. Judge must verify the board is both complete and valid.

**Judging Approach:**
- Function returns void
- After execution, inspect the modified board
- Verify completeness (no empty cells)
- Verify correctness (all Sudoku rules satisfied)
- Compare against oracle solution

**Key Considerations:**
1. **Void Return Challenge:**
   - Can't compare return values
   - Must inspect the modified 2D array
   - Need deep copy for user and oracle

2. **Completeness Check:**
   - No cells should contain '.' (empty marker)
   - All 81 cells must have digits 1-9
   
3. **Correctness Validation:**
   - **Row constraint**: Each row has digits 1-9 exactly once
   - **Column constraint**: Each column has digits 1-9 exactly once
   - **Box constraint**: Each 3×3 sub-box has digits 1-9 exactly once

4. **Deep Copy Requirement:**
   - Create separate board copies for user and oracle
   - Both start with same puzzle state
   - Both solve independently

5. **Uniqueness:**
   - Problem guarantees exactly one solution
   - User's solution should match oracle's exactly
   - No ambiguity in final board state

6. **Edge Cases:**
   - Nearly complete board (only 1-2 empty cells)
   - Completely empty board
   - Board with multiple solutions (shouldn't happen per constraints)
   - Invalid initial state (shouldn't happen per constraints)

7. **Validation Strategy:**
   - First check: Is board complete?
   - Second check: Does it satisfy all constraints?
   - Third check: Does it match oracle's solution?

---

### **Question 10: 4Sum**

**Category:** Combinatorial Search with Unordered Output  
**Return Type:** `List<List<Integer>>`  
**Node Type:** `null`  
**isOutputOrderMatters:** `false`

#### Judging Strategy
⚠️ **Requires unordered set comparison with normalization**

**Why:** Output is a list of quadruplets like `[[-2,-1,1,2], [-2,0,0,2]]`. Both the order of quadruplets AND the order within each quadruplet don't matter for correctness.

**Judging Approach:**
- Normalize each quadruplet (sort internally)
- Convert to set of tuples for unordered comparison
- Verify no duplicate quadruplets

**Key Considerations:**
1. **Double Level Ordering:**
   - **List order doesn't matter**: `[[1,2,3,4], [5,6,7,8]]` equals `[[5,6,7,8], [1,2,3,4]]`
   - **Quadruplet internal order doesn't matter**: `[1,0,-1,0]` equals `[-1,0,0,1]`

2. **Normalization Strategy:**
   - Sort each quadruplet internally: `[1,0,-1,0]` → `[-1,0,0,1]`
   - Convert to tuple for hashability
   - Use set comparison to ignore list order

3. **Uniqueness Requirement:**
   - No duplicate quadruplets in result
   - `[[1,2,3,4], [1,2,3,4]]` is invalid (duplicate)
   - Set comparison naturally handles this

4. **Edge Cases:**
   - All zeros: `[0,0,0,0]` with target 0
   - All same number: `[2,2,2,2,2]` with target 8
   - No valid quadruplets (empty result)
   - Negative numbers
   - Large target values (overflow concerns)

5. **What to Compare:**
   - ✅ Set of normalized quadruplets
   - ❌ Don't compare list order
   - ❌ Don't compare internal quadruplet order

**Example:**
```
User:    [[-2,-1,1,2], [-2,0,0,2], [-1,0,0,1]]
Oracle:  [[-1,0,0,1], [-2,-1,1,2], [-2,0,0,2]]

After normalization (both already sorted internally):
User set:   {(-2,-1,1,2), (-2,0,0,2), (-1,0,0,1)}
Oracle set: {(-1,0,0,1), (-2,-1,1,2), (-2,0,0,2)}

Set equality: ✅ PASS (same elements, order ignored)
```

---

## High-Level Strategy Checklist

### 1. **Void Return Type Handler** 🔴 CRITICAL

**Strategy:**
- When `returnType == "void"`, don't compare function outputs
- Instead, inspect the **modified input structure** after execution
- Requires deep copying inputs before passing to user/oracle

**Affected Questions:** #6, #7, #9

**Key Points:**
- Standard comparison fails: `None == None` always true
- Must traverse/inspect the modified data structure
- Each execution needs independent input copy

---

### 2. **Deep Copy Mechanism** 🔴 CRITICAL

**Strategy:**
- Before execution: Create independent copies of input for user and oracle
- Each gets their own copy to modify
- After execution: Compare the two modified copies

**When Needed:**
- All void return type questions
- Any in-place modification problem

**Data Structures:**
- TreeNode: Recursive deep copy
- ListNode: Iterative deep copy with cycle prevention
- 2D Arrays: Deep copy each row
- Graph: Clone with visited tracking

**Why Critical:**
```
❌ Using same input for both:
  - User modifies input
  - Oracle receives already-modified input
  - Comparison is meaningless

✅ Using separate copies:
  - User modifies their copy
  - Oracle modifies their copy
  - Compare the two independently modified copies
```

---

### 3. **Unordered Output Comparison** ⚠️

**Strategy:**
- When `isOutputOrderMatters == false`, use collection-based comparison
- Convert lists to sets or normalized forms
- Ignore ordering in comparison

**Affected Questions:** #3, #5, #8, #10

**Normalization Strategies:**
- **Lists of primitives**: Convert to set
- **Lists of lists**: Sort each inner list, then use set of tuples
- **Graph edges**: Normalize edge direction `[a,b]` → `(min(a,b), max(a,b))`

**Key Points:**
- Duplicate detection automatically handled by sets
- Must normalize before comparison (e.g., sort edges)
- Don't enforce output order in test expectations

---

### 4. **Custom Node Type Handling** ⚠️

**Strategy:**
- Can't compare object references directly
- Must serialize to comparable format (arrays, dicts, strings)
- Need traversal algorithms for each type

**Node Types:**

**TREE_NODE:**
- Serialize to array (level-order or preorder)
- Recursive equality checker for structure
- Handle null children explicitly

**LIST_NODE:**
- Serialize to array of values
- Detect cycles during traversal
- Track visited nodes by reference

**GRAPH_NODE:**
- Serialize with BFS/DFS
- Verify isomorphism (same structure)
- Handle cycles (they're valid in graphs)

**Key Points:**
- Object equality checks fail (different memory addresses)
- Need cycle detection for LinkedList and Graph
- Tree equality: values + structure + null positions

---

### 5. **Structural Validation** 🔴

**Strategy:**
- Some problems require verifying constraints, not just output equality
- Validate problem-specific rules

**Examples:**

**Sudoku (#9):**
- Verify all cells filled (no '.')
- Verify row constraints (1-9 unique per row)
- Verify column constraints (1-9 unique per column)
- Verify 3×3 box constraints

**Flatten Tree (#6):**
- Verify all left pointers are null
- Verify right pointers form linked list
- Verify preorder matches

**Reorder List (#7):**
- Verify no cycles created
- Verify specific reordering pattern

**Key Points:**
- Don't just compare outputs
- Validate structural constraints
- Catch invalid states (cycles, incomplete solutions)

---

### 6. **Round-Trip Testing** 🔴

**Strategy:**
- For encode/decode or serialize/deserialize problems
- Test: `decode(encode(input)) == input`
- User's encoding format can differ from oracle's

**Affected Questions:** #4

**Key Points:**
- Don't compare encoded strings directly
- Only verify round-trip correctness
- Allow arbitrary encoding formats
- Verify structural equality of decoded result

---

### 7. **Deep Copy Verification** 🔴

**Strategy:**
- For clone/copy problems, verify no shared references
- Check structural equality separately from reference independence

**Affected Questions:** #8

**What to Verify:**
1. **Reference Independence:**
   - `original_node is not clone_node` for all nodes
   - Modifying clone doesn't affect original
   
2. **Structural Equality:**
   - Same values
   - Same connections
   - Same topology

**Key Points:**
- Two separate checks needed
- Can't just check structure (might be shallow copy)
- Can't just check references (might be different structure)

---

## Edge Cases to Consider

### **General Edge Cases:**

1. **Null/Empty Inputs:**
   - Empty tree: `root = null`
   - Empty list: `head = null`
   - Empty board: `board = [[]]`
   - Empty graph: `node = null`
   - Empty array: `nums = []`
   
   **Strategy:** Test separately, many algorithms have special handling for empty inputs

2. **Single Element:**
   - Single node tree
   - Single node list
   - Single element array
   
   **Strategy:** Often base case for recursive algorithms

3. **Boundary Values:**
   - Maximum/minimum integer values
   - Maximum allowed input size
   - All zeros or all same values
   
   **Strategy:** Test integer overflow, edge case optimizations

---

### **Data Structure Specific:**

**LinkedList:**
1. **Cycles:**
   - User's buggy code might create circular references
   - Detect during traversal to prevent infinite loops
   
2. **Broken Links:**
   - User might accidentally break chain
   - Some nodes become unreachable
   
3. **Memory Leaks:**
   - Old references not properly cleaned up
   - Not testable but worth considering

**Binary Tree:**
1. **Skewed Trees:**
   - All left children (left-skewed)
   - All right children (right-skewed)
   
2. **Null Children:**
   - Different null patterns matter for structure
   - `[1, null, 2]` ≠ `[1, 2, null]`

**Graph:**
1. **Cycles:**
   - Valid in graphs, must handle gracefully
   - Track visited nodes during traversal
   
2. **Disconnected Components:**
   - Some nodes unreachable from starting node
   - May or may not be valid depending on problem
   
3. **Self-loops:**
   - Node connected to itself
   - Valid graph structure

**2D Arrays:**
1. **Jagged Arrays:**
   - Rows of different lengths
   - Usually invalid but check constraints
   
2. **Empty Rows:**
   - `[[]]` vs `[]` vs `[[],[]]`

---

### **Algorithm Specific:**

**Void Return Types:**
1. **No Modification:**
   - User's code doesn't modify anything
   - Result matches input
   
2. **Partial Modification:**
   - Some cells/nodes left unchanged
   - May or may not be valid

**Unordered Output:**
1. **Duplicates:**
   - Result contains duplicate entries
   - Usually invalid
   
2. **Missing Elements:**
   - Result has fewer elements than expected
   
3. **Extra Elements:**
   - Result has more elements than expected

**Deep Copy:**
1. **Shallow Copy:**
   - User returns clone that shares some references
   - Appears equal but modifying one affects the other
   
2. **Incomplete Copy:**
   - Some nodes/edges missing from clone

---

## Summary: What Makes These Questions Different

### **Standard Judging Works For:**
- ✅ Question #1: Trapping Rain Water II (simple int return)

### **Requires Special Handling:**

**40% - Void Return Types** (Questions #6, #7, #9, #4):
- ❌ Can't compare return values (both return nothing)
- ✅ Must inspect modified input structure
- ✅ Need deep copy mechanism
- ✅ Traverse/serialize modified structure for comparison

**50% - Unordered Output** (Questions #3, #5, #8, #10):
- ❌ Can't use direct list equality
- ✅ Must use set-based comparison
- ✅ Need normalization (sort edges, tuples)
- ✅ Order doesn't matter in result

**70% - Custom Node Types** (Questions #2, #3, #4, #6, #7, #8):
- ❌ Can't compare object references
- ✅ Must serialize to comparable format
- ✅ Need traversal algorithms
- ✅ Require cycle detection for LinkedList/Graph

**30% - Special Validation** (Questions #4, #8, #9):
- ❌ Output equality alone insufficient
- ✅ Must verify structural constraints
- ✅ Round-trip testing for encode/decode
- ✅ Deep copy verification for clone problems
- ✅ Constraint validation for Sudoku

---

## Platform Integration Considerations

### **Metadata Fields That Drive Judging:**

1. **`returnType`:**
   - If `"void"` → Special handling required
   - If custom type (`ListNode`, `TreeNode`, `Node`) → Need serialization
   - If `List<List<>>` → May need normalization

2. **`nodeType`:**
   - `"TREE_NODE"` → Tree traversal/comparison needed
   - `"LIST_NODE"` → LinkedList serialization needed
   - `"GRAPH_NODE"` → Graph isomorphism needed
   - `null` → Standard types

3. **`isOutputOrderMatters`:**
   - `false` → Use unordered comparison
   - `true` → Use ordered comparison

### **Judging Flow Decision Tree:**

```
Is returnType == "void"?
├─ YES → Deep copy input, execute both, inspect modified structures
└─ NO → Execute both, get return values
    └─ Is nodeType custom (TREE/LIST/GRAPH)?
        ├─ YES → Serialize both results
        └─ NO → Use results directly
            └─ Is isOutputOrderMatters == false?
                ├─ YES → Normalize and use set comparison
                └─ NO → Direct equality comparison
```

### **What Your Platform Needs:**

1. **Deep Copy Infrastructure:**
   - For all custom node types
   - Activated when returnType is void

2. **Serialization Functions:**
   - Tree → Array conversion
   - LinkedList → Array conversion
   - Graph → Adjacency representation

3. **Cycle Detection:**
   - For LinkedList traversal
   - For Graph traversal

4. **Comparison Strategies:**
   - Direct equality
   - Set-based unordered comparison
   - Structural equality (trees)
   - Graph isomorphism

5. **Validation Functions:**
   - Sudoku constraint checker
   - Tree structure validator (for flattening)
   - Deep copy verifier (no shared references)

---

## Conclusion

**Standard Model Limitations:**
- Only 1 out of 10 questions (10%) can use simple equality comparison
- 9 out of 10 questions (90%) require specialized judging logic

**Three Critical Capabilities Needed:**

1. **🔴 Void Return Handling (40% of questions):**
   - Inspect modified state instead of return value
   - Deep copy inputs before execution
   - Traverse/serialize results for comparison

2. **⚠️ Unordered Comparison (50% of questions):**
   - Set-based comparison instead of list equality
   - Normalization of nested structures
   - Handle edge/tuple ordering

3. **🔴 Custom Node Type Support (70% of questions):**
   - Serialization to comparable formats
   - Cycle detection for traversals
   - Structural equality checkers

**Complexity Distribution:**
- ✅ **Simple** (10%): Direct output comparison
- ⚠️ **Moderate** (30%): Unordered comparison or serialization
- 🔴 **Complex** (60%): Void returns, deep copy verification, constraint validation

Your platform's judging system must handle these three dimensions to support advanced LeetCode-style problems. The metadata fields `returnType`, `nodeType`, and `isOutputOrderMatters` provide the signals needed to route each question to the appropriate judging strategy.