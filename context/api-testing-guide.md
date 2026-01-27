# AlgoCrack Problem Service - API Testing Guide

> **Base URL:** `http://localhost:8080`
> 
> **Content-Type:** `application/json`

---

## Table of Contents

1. [Tags API](#1-tags-api)
2. [Questions API](#2-questions-api)
3. [Test Cases API](#3-test-cases-api)
4. [Solutions API](#4-solutions-api)

---

## 1. Tags API

> **Base Path:** `/api/v1/tags`

Tags must be created **before** creating questions that reference them.

---

### 1.1 Create Tag

**Endpoint:** `POST /api/v1/tags`

**JSON Payload:**
```json
{
    "name": "Array",
    "description": "Problems involving array manipulation and traversal"
}
```

**Expected Response:** `201 Created` (No body)

---

### 1.2 Create More Tags (for use in questions)

**JSON Payload:**
```json
{
    "name": "Hash Table",
    "description": "Problems using hash maps for O(1) lookups"
}
```

**Expected Response:** `201 Created`

---

```json
{
    "name": "Two Pointers",
    "description": "Problems using two pointer technique for optimal solutions"
}
```

**Expected Response:** `201 Created`

---

```json
{
    "name": "Dynamic Programming",
    "description": "Problems requiring optimal substructure and overlapping subproblems"
}
```

**Expected Response:** `201 Created`

---

```json
{
    "name": "String",
    "description": "String manipulation and pattern matching problems"
}
```

**Expected Response:** `201 Created`

---

### 1.3 List All Tags

**Endpoint:** `GET /api/v1/tags`

**JSON Payload:** None

**Expected Response:**
```json
[
    {
        "id": 1,
        "name": "Array",
        "description": "Problems involving array manipulation and traversal"
    },
    {
        "id": 2,
        "name": "Hash Table",
        "description": "Problems using hash maps for O(1) lookups"
    },
    {
        "id": 3,
        "name": "Two Pointers",
        "description": "Problems using two pointer technique for optimal solutions"
    },
    {
        "id": 4,
        "name": "Dynamic Programming",
        "description": "Problems requiring optimal substructure and overlapping subproblems"
    },
    {
        "id": 5,
        "name": "String",
        "description": "String manipulation and pattern matching problems"
    }
]
```

---

### 1.4 Get Tag by ID

**Endpoint:** `GET /api/v1/tags/{id}`

**Example:** `GET /api/v1/tags/1`

**JSON Payload:** None

**Expected Response:**
```json
{
    "id": 1,
    "name": "Array",
    "description": "Problems involving array manipulation and traversal"
}
```

---

### 1.5 Delete Tag

**Endpoint:** `DELETE /api/v1/tags/{id}`

**Example:** `DELETE /api/v1/tags/5`

**JSON Payload:** None

**Expected Response:** `204 No Content`

---

## 2. Questions API

> **Base Path:** `/api/v1/questions`

---

### 2.1 Create Question - Two Sum (Easy)

**Endpoint:** `POST /api/v1/questions`

**JSON Payload:**
```json
{
    "questionTitle": "Two Sum",
    "questionDescription": "Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the same element twice.\n\nYou can return the answer in any order.\n\n**Example 1:**\n```\nInput: nums = [2,7,11,15], target = 9\nOutput: [0,1]\nExplanation: Because nums[0] + nums[1] == 9, we return [0, 1].\n```\n\n**Example 2:**\n```\nInput: nums = [3,2,4], target = 6\nOutput: [1,2]\n```\n\n**Example 3:**\n```\nInput: nums = [3,3], target = 6\nOutput: [0,1]\n```",
    "constraints": "- 2 <= nums.length <= 10^4\n- -10^9 <= nums[i] <= 10^9\n- -10^9 <= target <= 10^9\n- Only one valid answer exists.",
    "difficultyLevel": "Easy",
    "company": "Google",
    "timeoutLimit": 5,
    "isOutputOrderMatters": false,
    "tags": [
        { "name": "Array" },
        { "name": "Hash Table" }
    ],
    "testCases": [
        {
            "input": "[[2,7,11,15], 9]",
            "expectedOutput": "[0,1]",
            "orderIndex": 1,
            "isHidden": false
        },
        {
            "input": "[[3,2,4], 6]",
            "expectedOutput": "[1,2]",
            "orderIndex": 2,
            "isHidden": false
        },
        {
            "input": "[[3,3], 6]",
            "expectedOutput": "[0,1]",
            "orderIndex": 3,
            "isHidden": false
        },
        {
            "input": "[[1,5,8,3,9,2], 11]",
            "expectedOutput": "[2,3]",
            "orderIndex": 4,
            "isHidden": true
        },
        {
            "input": "[[-1,-2,-3,-4,-5], -8]",
            "expectedOutput": "[2,4]",
            "orderIndex": 5,
            "isHidden": true
        }
    ],
    "metadataList": [
        {
            "functionName": "twoSum",
            "returnType": "List<int>",
            "paramTypes": ["List<int>", "int"],
            "paramNames": ["nums", "target"],
            "language": "JAVA",
            "codeTemplate": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // Write your code here\n    }\n}",
            "executionStrategy": "STANDARD",
            "customInputEnabled": true
        },
        {
            "functionName": "twoSum",
            "returnType": "List<int>",
            "paramTypes": ["List<int>", "int"],
            "paramNames": ["nums", "target"],
            "language": "PYTHON",
            "codeTemplate": "class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        # Write your code here\n        pass",
            "executionStrategy": "STANDARD",
            "customInputEnabled": true
        }
    ],
    "solution": [
        {
            "code": "import java.util.HashMap;\nimport java.util.Map;\n\nclass Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[] { map.get(complement), i };\n            }\n            map.put(nums[i], i);\n        }\n        throw new IllegalArgumentException(\"No two sum solution\");\n    }\n}",
            "language": "JAVA"
        },
        {
            "code": "class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        seen = {}\n        for i, num in enumerate(nums):\n            complement = target - num\n            if complement in seen:\n                return [seen[complement], i]\n            seen[num] = i\n        return []",
            "language": "PYTHON"
        }
    ]
}
```

**Expected Response:**
```json
{
    "questionId": 1,
    "message": "Question created successfully"
}
```

---

### 2.2 Create Question - Longest Palindromic Substring (Medium)

**Endpoint:** `POST /api/v1/questions`

**JSON Payload:**
```json
{
    "questionTitle": "Longest Palindromic Substring",
    "questionDescription": "Given a string `s`, return the longest palindromic substring in `s`.\n\nA **palindrome** is a string that reads the same forward and backward.\n\n**Example 1:**\n```\nInput: s = \"babad\"\nOutput: \"bab\"\nExplanation: \"aba\" is also a valid answer.\n```\n\n**Example 2:**\n```\nInput: s = \"cbbd\"\nOutput: \"bb\"\n```",
    "constraints": "- 1 <= s.length <= 1000\n- s consist of only digits and English letters.",
    "difficultyLevel": "Medium",
    "company": "Amazon",
    "timeoutLimit": 10,
    "isOutputOrderMatters": true,
    "tags": [
        { "name": "String" },
        { "name": "Dynamic Programming" }
    ],
    "testCases": [
        {
            "input": "[\"babad\"]",
            "expectedOutput": "\"bab\"",
            "orderIndex": 1,
            "isHidden": false
        },
        {
            "input": "[\"cbbd\"]",
            "expectedOutput": "\"bb\"",
            "orderIndex": 2,
            "isHidden": false
        },
        {
            "input": "[\"a\"]",
            "expectedOutput": "\"a\"",
            "orderIndex": 3,
            "isHidden": false
        },
        {
            "input": "[\"racecar\"]",
            "expectedOutput": "\"racecar\"",
            "orderIndex": 4,
            "isHidden": true
        },
        {
            "input": "[\"abcdefg\"]",
            "expectedOutput": "\"a\"",
            "orderIndex": 5,
            "isHidden": true
        }
    ],
    "metadataList": [
        {
            "functionName": "longestPalindrome",
            "returnType": "string",
            "paramTypes": ["string"],
            "paramNames": ["s"],
            "language": "JAVA",
            "codeTemplate": "class Solution {\n    public String longestPalindrome(String s) {\n        // Write your code here\n    }\n}",
            "executionStrategy": "STANDARD",
            "customInputEnabled": true
        }
    ],
    "solution": [
        {
            "code": "class Solution {\n    public String longestPalindrome(String s) {\n        if (s == null || s.length() < 1) return \"\";\n        int start = 0, end = 0;\n        for (int i = 0; i < s.length(); i++) {\n            int len1 = expandAroundCenter(s, i, i);\n            int len2 = expandAroundCenter(s, i, i + 1);\n            int len = Math.max(len1, len2);\n            if (len > end - start) {\n                start = i - (len - 1) / 2;\n                end = i + len / 2;\n            }\n        }\n        return s.substring(start, end + 1);\n    }\n\n    private int expandAroundCenter(String s, int left, int right) {\n        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {\n            left--;\n            right++;\n        }\n        return right - left - 1;\n    }\n}",
            "language": "JAVA"
        }
    ]
}
```

**Expected Response:**
```json
{
    "questionId": 2,
    "message": "Question created successfully"
}
```

---

### 2.3 Create Question - Trapping Rain Water (Hard)

**Endpoint:** `POST /api/v1/questions`

**JSON Payload:**
```json
{
    "questionTitle": "Trapping Rain Water",
    "questionDescription": "Given `n` non-negative integers representing an elevation map where the width of each bar is `1`, compute how much water it can trap after raining.\n\n**Example 1:**\n```\nInput: height = [0,1,0,2,1,0,1,3,2,1,2,1]\nOutput: 6\nExplanation: The elevation map is represented by array [0,1,0,2,1,0,1,3,2,1,2,1]. In this case, 6 units of rain water are being trapped.\n```\n\n**Example 2:**\n```\nInput: height = [4,2,0,3,2,5]\nOutput: 9\n```",
    "constraints": "- n == height.length\n- 1 <= n <= 2 * 10^4\n- 0 <= height[i] <= 10^5",
    "difficultyLevel": "Hard",
    "company": "Microsoft",
    "timeoutLimit": 5,
    "isOutputOrderMatters": true,
    "tags": [
        { "name": "Array" },
        { "name": "Two Pointers" },
        { "name": "Dynamic Programming" }
    ],
    "testCases": [
        {
            "input": "[[0,1,0,2,1,0,1,3,2,1,2,1]]",
            "expectedOutput": "6",
            "orderIndex": 1,
            "isHidden": false
        },
        {
            "input": "[[4,2,0,3,2,5]]",
            "expectedOutput": "9",
            "orderIndex": 2,
            "isHidden": false
        },
        {
            "input": "[[1,2,3,4,5]]",
            "expectedOutput": "0",
            "orderIndex": 3,
            "isHidden": false
        },
        {
            "input": "[[5,4,3,2,1]]",
            "expectedOutput": "0",
            "orderIndex": 4,
            "isHidden": true
        },
        {
            "input": "[[3,0,0,2,0,4]]",
            "expectedOutput": "10",
            "orderIndex": 5,
            "isHidden": true
        }
    ],
    "metadataList": [
        {
            "functionName": "trap",
            "returnType": "int",
            "paramTypes": ["List<int>"],
            "paramNames": ["height"],
            "language": "JAVA",
            "codeTemplate": "class Solution {\n    public int trap(int[] height) {\n        // Write your code here\n    }\n}",
            "executionStrategy": "STANDARD",
            "customInputEnabled": true
        }
    ],
    "solution": [
        {
            "code": "class Solution {\n    public int trap(int[] height) {\n        if (height == null || height.length == 0) return 0;\n        \n        int left = 0, right = height.length - 1;\n        int leftMax = 0, rightMax = 0;\n        int water = 0;\n        \n        while (left < right) {\n            if (height[left] < height[right]) {\n                if (height[left] >= leftMax) {\n                    leftMax = height[left];\n                } else {\n                    water += leftMax - height[left];\n                }\n                left++;\n            } else {\n                if (height[right] >= rightMax) {\n                    rightMax = height[right];\n                } else {\n                    water += rightMax - height[right];\n                }\n                right--;\n            }\n        }\n        return water;\n    }\n}",
            "language": "JAVA"
        }
    ]
}
```

**Expected Response:**
```json
{
    "questionId": 3,
    "message": "Question created successfully"
}
```

---

### 2.4 Create Question - Valid Parentheses (Easy)

**Endpoint:** `POST /api/v1/questions`

**JSON Payload:**
```json
{
    "questionTitle": "Valid Parentheses",
    "questionDescription": "Given a string `s` containing just the characters `'('`, `')'`, `'{'`, `'}'`, `'['` and `']'`, determine if the input string is valid.\n\nAn input string is valid if:\n1. Open brackets must be closed by the same type of brackets.\n2. Open brackets must be closed in the correct order.\n3. Every close bracket has a corresponding open bracket of the same type.\n\n**Example 1:**\n```\nInput: s = \"()\"\nOutput: true\n```\n\n**Example 2:**\n```\nInput: s = \"()[]{}\"\nOutput: true\n```\n\n**Example 3:**\n```\nInput: s = \"(]\"\nOutput: false\n```\n\n**Example 4:**\n```\nInput: s = \"([)]\"\nOutput: false\n```\n\n**Example 5:**\n```\nInput: s = \"{[]}\"\nOutput: true\n```",
    "constraints": "- 1 <= s.length <= 10^4\n- s consists of parentheses only '()[]{}'.",
    "difficultyLevel": "Easy",
    "company": "Meta",
    "timeoutLimit": 5,
    "isOutputOrderMatters": true,
    "tags": [
        { "name": "String" }
    ],
    "testCases": [
        {
            "input": "[\"()\"]",
            "expectedOutput": "true",
            "orderIndex": 1,
            "isHidden": false
        },
        {
            "input": "[\"()[]{}\"]",
            "expectedOutput": "true",
            "orderIndex": 2,
            "isHidden": false
        },
        {
            "input": "[\"(]\"]",
            "expectedOutput": "false",
            "orderIndex": 3,
            "isHidden": false
        },
        {
            "input": "[\"([)]\"]",
            "expectedOutput": "false",
            "orderIndex": 4,
            "isHidden": true
        },
        {
            "input": "[\"{[]}\"]",
            "expectedOutput": "true",
            "orderIndex": 5,
            "isHidden": true
        },
        {
            "input": "[\"(((((((((()))))))))\"]",
            "expectedOutput": "false",
            "orderIndex": 6,
            "isHidden": true
        }
    ],
    "metadataList": [
        {
            "functionName": "isValid",
            "returnType": "boolean",
            "paramTypes": ["string"],
            "paramNames": ["s"],
            "language": "JAVA",
            "codeTemplate": "class Solution {\n    public boolean isValid(String s) {\n        // Write your code here\n    }\n}",
            "executionStrategy": "STANDARD",
            "customInputEnabled": true
        }
    ],
    "solution": [
        {
            "code": "import java.util.Stack;\n\nclass Solution {\n    public boolean isValid(String s) {\n        Stack<Character> stack = new Stack<>();\n        for (char c : s.toCharArray()) {\n            if (c == '(' || c == '{' || c == '[') {\n                stack.push(c);\n            } else {\n                if (stack.isEmpty()) return false;\n                char top = stack.pop();\n                if (c == ')' && top != '(') return false;\n                if (c == '}' && top != '{') return false;\n                if (c == ']' && top != '[') return false;\n            }\n        }\n        return stack.isEmpty();\n    }\n}",
            "language": "JAVA"
        }
    ]
}
```

**Expected Response:**
```json
{
    "questionId": 4,
    "message": "Question created successfully"
}
```

---

### 2.5 List Questions (with Pagination and Filtering)

**Endpoint:** `GET /api/v1/questions`

#### 2.5.1 Basic Pagination

**Request:** `GET /api/v1/questions?page=0&size=10`

**Expected Response:**
```json
{
    "content": [
        {
            "id": 1,
            "questionTitle": "Two Sum",
            "difficultyLevel": "Easy",
            "tags": ["Array", "Hash Table"],
            "company": "Google",
            "acceptanceRate": null,
            "totalSubmissions": null
        },
        {
            "id": 2,
            "questionTitle": "Longest Palindromic Substring",
            "difficultyLevel": "Medium",
            "tags": ["String", "Dynamic Programming"],
            "company": "Amazon",
            "acceptanceRate": null,
            "totalSubmissions": null
        },
        {
            "id": 3,
            "questionTitle": "Trapping Rain Water",
            "difficultyLevel": "Hard",
            "tags": ["Array", "Two Pointers", "Dynamic Programming"],
            "company": "Microsoft",
            "acceptanceRate": null,
            "totalSubmissions": null
        },
        {
            "id": 4,
            "questionTitle": "Valid Parentheses",
            "difficultyLevel": "Easy",
            "tags": ["String"],
            "company": "Meta",
            "acceptanceRate": null,
            "totalSubmissions": null
        }
    ],
    "pageable": {
        "pageNumber": 0,
        "pageSize": 10,
        "offset": 0
    },
    "totalElements": 4,
    "totalPages": 1,
    "last": true,
    "first": true,
    "numberOfElements": 4,
    "empty": false
}
```

---

#### 2.5.2 Filter by Difficulty

**Request:** `GET /api/v1/questions?difficulty=Easy`

**Expected Response:**
```json
{
    "content": [
        {
            "id": 1,
            "questionTitle": "Two Sum",
            "difficultyLevel": "Easy",
            "tags": ["Array", "Hash Table"],
            "company": "Google",
            "acceptanceRate": null,
            "totalSubmissions": null
        },
        {
            "id": 4,
            "questionTitle": "Valid Parentheses",
            "difficultyLevel": "Easy",
            "tags": ["String"],
            "company": "Meta",
            "acceptanceRate": null,
            "totalSubmissions": null
        }
    ],
    "totalElements": 2,
    "totalPages": 1
}
```

---

#### 2.5.3 Filter by Tag

**Request:** `GET /api/v1/questions?tag=Array`

**Expected Response:**
```json
{
    "content": [
        {
            "id": 1,
            "questionTitle": "Two Sum",
            "difficultyLevel": "Easy",
            "tags": ["Array", "Hash Table"],
            "company": "Google",
            "acceptanceRate": null,
            "totalSubmissions": null
        },
        {
            "id": 3,
            "questionTitle": "Trapping Rain Water",
            "difficultyLevel": "Hard",
            "tags": ["Array", "Two Pointers", "Dynamic Programming"],
            "company": "Microsoft",
            "acceptanceRate": null,
            "totalSubmissions": null
        }
    ],
    "totalElements": 2,
    "totalPages": 1
}
```

---

#### 2.5.4 Filter by Company

**Request:** `GET /api/v1/questions?company=Google`

**Expected Response:**
```json
{
    "content": [
        {
            "id": 1,
            "questionTitle": "Two Sum",
            "difficultyLevel": "Easy",
            "tags": ["Array", "Hash Table"],
            "company": "Google",
            "acceptanceRate": null,
            "totalSubmissions": null
        }
    ],
    "totalElements": 1,
    "totalPages": 1
}
```

---

#### 2.5.5 Search by Title

**Request:** `GET /api/v1/questions?search=palindrome`

**Expected Response:**
```json
{
    "content": [
        {
            "id": 2,
            "questionTitle": "Longest Palindromic Substring",
            "difficultyLevel": "Medium",
            "tags": ["String", "Dynamic Programming"],
            "company": "Amazon",
            "acceptanceRate": null,
            "totalSubmissions": null
        }
    ],
    "totalElements": 1,
    "totalPages": 1
}
```

---

#### 2.5.6 Combined Filters

**Request:** `GET /api/v1/questions?difficulty=Hard&tag=Dynamic%20Programming`

**Expected Response:**
```json
{
    "content": [
        {
            "id": 3,
            "questionTitle": "Trapping Rain Water",
            "difficultyLevel": "Hard",
            "tags": ["Array", "Two Pointers", "Dynamic Programming"],
            "company": "Microsoft",
            "acceptanceRate": null,
            "totalSubmissions": null
        }
    ],
    "totalElements": 1,
    "totalPages": 1
}
```

---

### 2.6 Get Question by ID

**Endpoint:** `GET /api/v1/questions/{id}`

**Example:** `GET /api/v1/questions/1`

**Expected Response:**
```json
{
    "id": 1,
    "questionTitle": "Two Sum",
    "questionDescription": "Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the same element twice.\n\nYou can return the answer in any order.\n\n**Example 1:**\n```\nInput: nums = [2,7,11,15], target = 9\nOutput: [0,1]\nExplanation: Because nums[0] + nums[1] == 9, we return [0, 1].\n```\n\n**Example 2:**\n```\nInput: nums = [3,2,4], target = 6\nOutput: [1,2]\n```\n\n**Example 3:**\n```\nInput: nums = [3,3], target = 6\nOutput: [0,1]\n```",
    "isOutputOrderMatters": false,
    "tags": ["Array", "Hash Table"],
    "difficultyLevel": "Easy",
    "company": "Google",
    "constraints": "- 2 <= nums.length <= 10^4\n- -10^9 <= nums[i] <= 10^9\n- -10^9 <= target <= 10^9\n- Only one valid answer exists."
}
```

---

### 2.7 Update Question

**Endpoint:** `PUT /api/v1/questions/{id}`

**Example:** `PUT /api/v1/questions/1`

**JSON Payload:**
```json
{
    "questionTitle": "Two Sum (Updated)",
    "questionDescription": "Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the same element twice.\n\nYou can return the answer in any order.\n\n**Follow-up:** Can you come up with an algorithm that is less than O(n²) time complexity?",
    "difficultyLevel": "Easy",
    "company": "Google, Amazon",
    "testCases": [
        {
            "input": "[[2,7,11,15], 9]",
            "expectedOutput": "[0,1]",
            "orderIndex": 1,
            "isHidden": false
        }
    ],
    "metadataList": [
        {
            "functionName": "twoSum",
            "returnType": "List<int>",
            "paramTypes": ["List<int>", "int"],
            "paramNames": ["nums", "target"],
            "language": "JAVA",
            "codeTemplate": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // Write your code here\n    }\n}",
            "executionStrategy": "STANDARD",
            "customInputEnabled": true
        }
    ],
    "tags": [
        { "name": "Array" },
        { "name": "Hash Table" },
        { "name": "Two Pointers" }
    ]
}
```

**Expected Response:**
```json
{
    "id": 1,
    "questionTitle": "Two Sum (Updated)",
    "questionDescription": "Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the same element twice.\n\nYou can return the answer in any order.\n\n**Follow-up:** Can you come up with an algorithm that is less than O(n²) time complexity?",
    "isOutputOrderMatters": false,
    "tags": ["Array", "Hash Table", "Two Pointers"],
    "difficultyLevel": "Easy",
    "company": "Google, Amazon",
    "constraints": "- 2 <= nums.length <= 10^4\n- -10^9 <= nums[i] <= 10^9\n- -10^9 <= target <= 10^9\n- Only one valid answer exists."
}
```

---

### 2.8 Delete Question

**Endpoint:** `DELETE /api/v1/questions/{id}`

**Example:** `DELETE /api/v1/questions/4`

**JSON Payload:** None

**Expected Response:** `204 No Content`

---

## 3. Test Cases API

> **Base Path:** `/api/v1/testcases`

---

### 3.1 Add Test Case to Question

**Endpoint:** `POST /api/v1/testcases`

**JSON Payload (for Question ID 1 - Two Sum):**
```json
{
    "questionId": 1,
    "input": "[[0,4,3,0], 0]",
    "expectedOutput": "[0,3]",
    "orderIndex": 6,
    "isHidden": true
}
```

**Expected Response:** `201 Created`

---

### 3.2 Add Another Test Case

**JSON Payload:**
```json
{
    "questionId": 1,
    "input": "[[1,2,3,4,5,6,7,8,9,10], 19]",
    "expectedOutput": "[8,9]",
    "orderIndex": 7,
    "isHidden": true
}
```

**Expected Response:** `201 Created`

---

### 3.3 Get Test Case by ID

**Endpoint:** `GET /api/v1/testcases/{id}`

**Example:** `GET /api/v1/testcases/1`

**Expected Response:**
```json
{
    "id": 1,
    "questionId": 1,
    "input": "[[2,7,11,15], 9]",
    "expectedOutput": "[0,1]",
    "orderIndex": 1,
    "isHidden": false
}
```

---

### 3.4 Get All Test Cases for a Question

**Endpoint:** `GET /api/v1/testcases/question/{questionId}`

**Example:** `GET /api/v1/testcases/question/1`

**Expected Response:**
```json
[
    {
        "id": 1,
        "questionId": 1,
        "input": "[[2,7,11,15], 9]",
        "expectedOutput": "[0,1]",
        "orderIndex": 1,
        "isHidden": false
    },
    {
        "id": 2,
        "questionId": 1,
        "input": "[[3,2,4], 6]",
        "expectedOutput": "[1,2]",
        "orderIndex": 2,
        "isHidden": false
    },
    {
        "id": 3,
        "questionId": 1,
        "input": "[[3,3], 6]",
        "expectedOutput": "[0,1]",
        "orderIndex": 3,
        "isHidden": false
    },
    {
        "id": 4,
        "questionId": 1,
        "input": "[[1,5,8,3,9,2], 11]",
        "expectedOutput": "[2,3]",
        "orderIndex": 4,
        "isHidden": true
    },
    {
        "id": 5,
        "questionId": 1,
        "input": "[[-1,-2,-3,-4,-5], -8]",
        "expectedOutput": "[2,4]",
        "orderIndex": 5,
        "isHidden": true
    }
]
```

---

### 3.5 Update Test Case

**Endpoint:** `PUT /api/v1/testcases/{id}`

**Example:** `PUT /api/v1/testcases/5`

**JSON Payload:**
```json
{
    "questionId": 1,
    "input": "[[-10,-20,-30,-40,-50], -80]",
    "expectedOutput": "[2,4]",
    "orderIndex": 5,
    "isHidden": true
}
```

**Expected Response:**
```json
{
    "id": 5,
    "questionId": 1,
    "input": "[[-10,-20,-30,-40,-50], -80]",
    "expectedOutput": "[2,4]",
    "orderIndex": 5,
    "isHidden": true
}
```

---

### 3.6 Delete Test Case

**Endpoint:** `DELETE /api/v1/testcases/{id}`

**Example:** `DELETE /api/v1/testcases/5`

**JSON Payload:** None

**Expected Response:** `204 No Content`

---

## 4. Solutions API

> **Base Path:** `/api/v1/solutions`

---

### 4.1 Add Solution to Question

**Endpoint:** `POST /api/v1/solutions`

**JSON Payload:**
```json
{
    "questionId": 1,
    "code": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // Brute force approach - O(n²)\n        for (int i = 0; i < nums.length; i++) {\n            for (int j = i + 1; j < nums.length; j++) {\n                if (nums[i] + nums[j] == target) {\n                    return new int[] { i, j };\n                }\n            }\n        }\n        return new int[] {};\n    }\n}",
    "language": "JAVA",
    "explanation": "Brute force approach: Check every pair of numbers. Time complexity O(n²), Space complexity O(1)."
}
```

**Expected Response:**
```json
{
    "id": 3,
    "code": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // Brute force approach - O(n²)\n        for (int i = 0; i < nums.length; i++) {\n            for (int j = i + 1; j < nums.length; j++) {\n                if (nums[i] + nums[j] == target) {\n                    return new int[] { i, j };\n                }\n            }\n        }\n        return new int[] {};\n    }\n}",
    "language": "JAVA",
    "explanation": "Brute force approach: Check every pair of numbers. Time complexity O(n²), Space complexity O(1).",
    "questionId": 1
}
```

---

### 4.2 Get All Solutions for a Question

**Endpoint:** `GET /api/v1/solutions/question/{questionId}`

**Example:** `GET /api/v1/solutions/question/1`

**Expected Response:**
```json
[
    {
        "id": 1,
        "code": "import java.util.HashMap;\nimport java.util.Map;\n\nclass Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[] { map.get(complement), i };\n            }\n            map.put(nums[i], i);\n        }\n        throw new IllegalArgumentException(\"No two sum solution\");\n    }\n}",
        "language": "JAVA",
        "explanation": null,
        "questionId": 1
    },
    {
        "id": 2,
        "code": "class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        seen = {}\n        for i, num in enumerate(nums):\n            complement = target - num\n            if complement in seen:\n                return [seen[complement], i]\n            seen[num] = i\n        return []",
        "language": "PYTHON",
        "explanation": null,
        "questionId": 1
    },
    {
        "id": 3,
        "code": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // Brute force approach - O(n²)\n        for (int i = 0; i < nums.length; i++) {\n            for (int j = i + 1; j < nums.length; j++) {\n                if (nums[i] + nums[j] == target) {\n                    return new int[] { i, j };\n                }\n            }\n        }\n        return new int[] {};\n    }\n}",
        "language": "JAVA",
        "explanation": "Brute force approach: Check every pair of numbers. Time complexity O(n²), Space complexity O(1).",
        "questionId": 1
    }
]
```

---

### 4.3 Update Solution

**Endpoint:** `PUT /api/v1/solutions/{solutionId}`

**Example:** `PUT /api/v1/solutions/1`

**JSON Payload:**
```json
{
    "code": "import java.util.HashMap;\nimport java.util.Map;\n\nclass Solution {\n    /**\n     * Optimal HashMap approach\n     * Time Complexity: O(n)\n     * Space Complexity: O(n)\n     */\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[] { map.get(complement), i };\n            }\n            map.put(nums[i], i);\n        }\n        throw new IllegalArgumentException(\"No two sum solution\");\n    }\n}",
    "language": "JAVA",
    "explanation": "HashMap approach: Store each number and its index. For each new number, check if its complement exists. Time: O(n), Space: O(n)."
}
```

**Expected Response:**
```json
{
    "id": 1,
    "code": "import java.util.HashMap;\nimport java.util.Map;\n\nclass Solution {\n    /**\n     * Optimal HashMap approach\n     * Time Complexity: O(n)\n     * Space Complexity: O(n)\n     */\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[] { map.get(complement), i };\n            }\n            map.put(nums[i], i);\n        }\n        throw new IllegalArgumentException(\"No two sum solution\");\n    }\n}",
    "language": "JAVA",
    "explanation": "HashMap approach: Store each number and its index. For each new number, check if its complement exists. Time: O(n), Space: O(n).",
    "questionId": 1
}
```

---

### 4.4 Delete Solution

**Endpoint:** `DELETE /api/v1/solutions/{solutionId}`

**Example:** `DELETE /api/v1/solutions/3`

**JSON Payload:** None

**Expected Response:** `204 No Content`

---

## Error Responses

All endpoints return appropriate error responses for validation failures and not-found scenarios.

### Validation Error (400 Bad Request)

**Example:** Creating a question without required fields

**JSON Payload:**
```json
{
    "questionTitle": "",
    "questionDescription": "Test"
}
```

**Expected Response:**
```json
{
    "timestamp": "2026-01-21T11:45:00.000+00:00",
    "status": 400,
    "error": "Bad Request",
    "message": "Validation failed",
    "errors": [
        "Question title is required",
        "At least one test case is required",
        "At least one metadata entry is required"
    ]
}
```

---

### Resource Not Found (404 Not Found)

**Example:** `GET /api/v1/questions/999`

**Expected Response:**
```json
{
    "timestamp": "2026-01-21T11:45:00.000+00:00",
    "status": 404,
    "error": "Not Found",
    "message": "Question not found with ID: 999"
}
```

---

### Tag Already Exists (400 Bad Request)

**Example:** Creating a duplicate tag

**JSON Payload:**
```json
{
    "name": "Array",
    "description": "Duplicate tag"
}
```

**Expected Response:**
```json
{
    "timestamp": "2026-01-21T11:45:00.000+00:00",
    "status": 400,
    "error": "Bad Request",
    "message": "Tag with name 'Array' already exists"
}
```

---

## Testing Order (Recommended)

1. **Create Tags first** (Array, Hash Table, Two Pointers, Dynamic Programming, String)
2. **Create Questions** (referencing existing tags)
3. **Add additional Test Cases** to questions
4. **Add additional Solutions** to questions
5. **Test List/Filter operations** on questions
6. **Test Update operations** on questions, test cases, and solutions
7. **Test Delete operations** (solutions → test cases → questions → tags)

---

## Quick cURL Commands

### Create Tag
```bash
curl -X POST http://localhost:8080/api/v1/tags \
  -H "Content-Type: application/json" \
  -d '{"name": "Array", "description": "Array problems"}'
```

### Create Question
```bash
curl -X POST http://localhost:8080/api/v1/questions \
  -H "Content-Type: application/json" \
  -d @two-sum.json
```

### List Questions
```bash
curl http://localhost:8080/api/v1/questions?page=0&size=10&difficulty=Easy
```

### Get Question
```bash
curl http://localhost:8080/api/v1/questions/1
```

---

*Document generated from AlgoCrack Problem Service codebase v2.0*
