# AlgoCrack Submission Service - API Testing Guide

> **Submission Service Port:** `http://localhost:8080`
> **Code Execution Engine Port:** `http://localhost:8081`
> **Content-Type:** `application/json`

---

## Table of Contents

1. [Submit Code for Execution](#1-submit-code-for-execution)
2. [Get Submission Status/Details](#2-get-submission-statusdetails)
3. [Get User Submission History](#3-get-user-submission-history)
4. [Test Scenarios by Question](#4-test-scenarios-by-question)
5. [WebSocket Real-time Updates](#5-websocket-real-time-updates)
6. [End-to-End Test Flow](#6-end-to-end-test-flow)

---

## 1. Submit Code for Execution

### Endpoint
```
POST /api/v1/submissions
```

### Request Headers
```
Content-Type: application/json
```

### Request Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | Long | Yes | ID of the user submitting code |
| `questionId` | Long | Yes | ID of the question (1-4 in database) |
| `language` | String | Yes | Programming language (`java`, `python`) |
| `code` | String | Yes | User's solution code |

### Response (202 Accepted)
```json
{
    "submissionId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "status": "QUEUED",
    "message": "Submission queued for processing"
}
```

---

## 2. Get Submission Status/Details

### Endpoint
```
GET /api/v1/submissions/{submissionId}
```

### Status Values

| Status | Description |
|--------|-------------|
| `QUEUED` | Waiting in queue |
| `COMPILING` | Code is being compiled |
| `RUNNING` | Executing test cases |
| `COMPLETED` | Execution finished (check verdict) |
| `FAILED` | System error occurred |

### Verdict Values

| Verdict | Description |
|---------|-------------|
| `ACCEPTED` | All test cases passed ✅ |
| `WRONG_ANSWER` | Output doesn't match expected |
| `TIME_LIMIT_EXCEEDED` | Execution took too long |
| `RUNTIME_ERROR` | Exception during execution |
| `COMPILATION_ERROR` | Code failed to compile |

---

## 3. Get User Submission History

### Endpoint
```
GET /api/v1/submissions/user/{userId}?page=0&size=10
```

---

## 4. Test Scenarios by Question

> **Questions in Database:**
> - Question 1: **Two Sum** (Easy)
> - Question 2: **Longest Palindromic Substring** (Medium)
> - Question 3: **Trapping Rain Water** (Hard)
> - Question 4: **Valid Parentheses** (Easy)

---

### 4.1 Two Sum (Question ID: 1)

#### ✅ Correct Solution (Expected: ACCEPTED)

```bash
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "questionId": 1,
    "language": "java",
    "code": "import java.util.HashMap;\nimport java.util.Map;\n\nclass Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[] { map.get(complement), i };\n            }\n            map.put(nums[i], i);\n        }\n        throw new IllegalArgumentException(\"No two sum solution\");\n    }\n}"
  }'
```

**JSON Payload (for Postman):**
```json
{
    "userId": 1,
    "questionId": 1,
    "language": "java",
    "code": "import java.util.HashMap;\nimport java.util.Map;\n\nclass Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[] { map.get(complement), i };\n            }\n            map.put(nums[i], i);\n        }\n        throw new IllegalArgumentException(\"No two sum solution\");\n    }\n}"
}
```

---

#### ✅ Brute Force Solution (Expected: ACCEPTED)

```json
{
    "userId": 1,
    "questionId": 1,
    "language": "java",
    "code": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        for (int i = 0; i < nums.length; i++) {\n            for (int j = i + 1; j < nums.length; j++) {\n                if (nums[i] + nums[j] == target) {\n                    return new int[]{i, j};\n                }\n            }\n        }\n        return new int[]{};\n    }\n}"
}
```

---

#### ❌ Wrong Answer (Returns wrong indices)

```json
{
    "userId": 1,
    "questionId": 1,
    "language": "java",
    "code": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        return new int[]{0, 0};\n    }\n}"
}
```

---

#### ❌ Compilation Error (Missing semicolon)

```json
{
    "userId": 1,
    "questionId": 1,
    "language": "java",
    "code": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        return new int[]{0, 1}\n    }\n}"
}
```

---

#### ❌ Runtime Error (ArrayIndexOutOfBounds)

```json
{
    "userId": 1,
    "questionId": 1,
    "language": "java",
    "code": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        return new int[]{nums[100], nums[200]};\n    }\n}"
}
```

---

### 4.2 Longest Palindromic Substring (Question ID: 2)

#### ✅ Correct Solution (Expected: ACCEPTED)

```json
{
    "userId": 1,
    "questionId": 2,
    "language": "java",
    "code": "class Solution {\n    public String longestPalindrome(String s) {\n        if (s == null || s.length() < 1) return \"\";\n        int start = 0, end = 0;\n        for (int i = 0; i < s.length(); i++) {\n            int len1 = expandAroundCenter(s, i, i);\n            int len2 = expandAroundCenter(s, i, i + 1);\n            int len = Math.max(len1, len2);\n            if (len > end - start) {\n                start = i - (len - 1) / 2;\n                end = i + len / 2;\n            }\n        }\n        return s.substring(start, end + 1);\n    }\n\n    private int expandAroundCenter(String s, int left, int right) {\n        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {\n            left--;\n            right++;\n        }\n        return right - left - 1;\n    }\n}"
}
```

---

#### ❌ Wrong Answer (Returns first character only)

```json
{
    "userId": 1,
    "questionId": 2,
    "language": "java",
    "code": "class Solution {\n    public String longestPalindrome(String s) {\n        return String.valueOf(s.charAt(0));\n    }\n}"
}
```

---

### 4.3 Trapping Rain Water (Question ID: 3)

#### ✅ Correct Solution - Two Pointers (Expected: ACCEPTED)

```json
{
    "userId": 1,
    "questionId": 3,
    "language": "java",
    "code": "class Solution {\n    public int trap(int[] height) {\n        if (height == null || height.length == 0) return 0;\n        \n        int left = 0, right = height.length - 1;\n        int leftMax = 0, rightMax = 0;\n        int water = 0;\n        \n        while (left < right) {\n            if (height[left] < height[right]) {\n                if (height[left] >= leftMax) {\n                    leftMax = height[left];\n                } else {\n                    water += leftMax - height[left];\n                }\n                left++;\n            } else {\n                if (height[right] >= rightMax) {\n                    rightMax = height[right];\n                } else {\n                    water += rightMax - height[right];\n                }\n                right--;\n            }\n        }\n        return water;\n    }\n}"
}
```

---

#### ❌ Wrong Answer (Always returns 0)

```json
{
    "userId": 1,
    "questionId": 3,
    "language": "java",
    "code": "class Solution {\n    public int trap(int[] height) {\n        return 0;\n    }\n}"
}
```

---

### 4.4 Valid Parentheses (Question ID: 4)

#### ✅ Correct Solution (Expected: ACCEPTED)

```json
{
    "userId": 1,
    "questionId": 4,
    "language": "java",
    "code": "import java.util.Stack;\n\nclass Solution {\n    public boolean isValid(String s) {\n        Stack<Character> stack = new Stack<>();\n        for (char c : s.toCharArray()) {\n            if (c == '(' || c == '{' || c == '[') {\n                stack.push(c);\n            } else {\n                if (stack.isEmpty()) return false;\n                char top = stack.pop();\n                if (c == ')' && top != '(') return false;\n                if (c == '}' && top != '{') return false;\n                if (c == ']' && top != '[') return false;\n            }\n        }\n        return stack.isEmpty();\n    }\n}"
}
```

---

#### ❌ Wrong Answer (Always returns true)

```json
{
    "userId": 1,
    "questionId": 4,
    "language": "java",
    "code": "class Solution {\n    public boolean isValid(String s) {\n        return true;\n    }\n}"
}
```

---

## 5. WebSocket Real-time Updates

### Endpoint
```
ws://localhost:8080/ws
```

### Subscribe Topic
```
/topic/submission/{submissionId}
```

### JavaScript Example
```javascript
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    stompClient.subscribe('/topic/submission/{submissionId}', function(message) {
        const data = JSON.parse(message.body);
        console.log('Update:', data);
    });
});
```

---

## 6. End-to-End Test Flow

### Step 1: Submit Code
```bash
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "questionId": 1,
    "language": "java",
    "code": "import java.util.HashMap;\nimport java.util.Map;\n\nclass Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[] { map.get(complement), i };\n            }\n            map.put(nums[i], i);\n        }\n        throw new IllegalArgumentException(\"No solution\");\n    }\n}"
  }')

echo "Submit Response: $RESPONSE"
SUBMISSION_ID=$(echo $RESPONSE | jq -r '.submissionId')
echo "Submission ID: $SUBMISSION_ID"
```

### Step 2: Poll for Results
```bash
while true; do
    RESULT=$(curl -s http://localhost:8080/api/v1/submissions/$SUBMISSION_ID)
    STATUS=$(echo $RESULT | jq -r '.status')
    echo "Status: $STATUS"
    
    if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
        echo "Final Result:"
        echo $RESULT | jq .
        break
    fi
    
    sleep 1
done
```

### Expected Output (Successful Submission)
```json
{
  "submissionId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "userId": 1,
  "questionId": 1,
  "language": "java",
  "status": "COMPLETED",
  "verdict": "ACCEPTED",
  "runtimeMs": 45,
  "memoryKb": 12048,
  "passedTestCases": 5,
  "totalTestCases": 5
}
```

---

## Quick Test Commands

### Test All 4 Questions (Correct Solutions)

```bash
# Question 1: Two Sum
curl -X POST http://localhost:8080/api/v1/submissions -H "Content-Type: application/json" -d '{"userId":1,"questionId":1,"language":"java","code":"import java.util.*;class Solution{public int[] twoSum(int[] nums,int target){Map<Integer,Integer> m=new HashMap<>();for(int i=0;i<nums.length;i++){if(m.containsKey(target-nums[i]))return new int[]{m.get(target-nums[i]),i};m.put(nums[i],i);}return new int[]{};}}"}'

# Question 2: Longest Palindrome
curl -X POST http://localhost:8080/api/v1/submissions -H "Content-Type: application/json" -d '{"userId":1,"questionId":2,"language":"java","code":"class Solution{public String longestPalindrome(String s){if(s==null||s.length()<1)return\"\";int start=0,end=0;for(int i=0;i<s.length();i++){int len1=expand(s,i,i);int len2=expand(s,i,i+1);int len=Math.max(len1,len2);if(len>end-start){start=i-(len-1)/2;end=i+len/2;}}return s.substring(start,end+1);}int expand(String s,int l,int r){while(l>=0&&r<s.length()&&s.charAt(l)==s.charAt(r)){l--;r++;}return r-l-1;}}"}'

# Question 3: Trapping Rain Water
curl -X POST http://localhost:8080/api/v1/submissions -H "Content-Type: application/json" -d '{"userId":1,"questionId":3,"language":"java","code":"class Solution{public int trap(int[] h){if(h==null||h.length==0)return 0;int l=0,r=h.length-1,lM=0,rM=0,w=0;while(l<r){if(h[l]<h[r]){if(h[l]>=lM)lM=h[l];else w+=lM-h[l];l++;}else{if(h[r]>=rM)rM=h[r];else w+=rM-h[r];r--;}}return w;}}"}'

# Question 4: Valid Parentheses
curl -X POST http://localhost:8080/api/v1/submissions -H "Content-Type: application/json" -d '{"userId":1,"questionId":4,"language":"java","code":"import java.util.*;class Solution{public boolean isValid(String s){Stack<Character> st=new Stack<>();for(char c:s.toCharArray()){if(c=='\''('\''||c=='\''{'\''||c=='\''['\'')st.push(c);else{if(st.isEmpty())return false;char t=st.pop();if(c=='\'')'\''&&t!='\''('\''||c=='\''}'\''&&t!='\''{'\''||c=='\'']'\''&&t!='\''['\'')return false;}}return st.isEmpty();}}"}'
```

---

## Error Responses

### 400 Bad Request
```json
{
    "timestamp": "2026-01-23T16:00:00",
    "status": 400,
    "error": "Bad Request",
    "message": "Required field 'code' is missing"
}
```

### 404 Not Found
```json
{
    "timestamp": "2026-01-23T16:00:00",
    "status": 404,
    "error": "Not Found",
    "message": "Question not found with id: 99"
}
```

### 500 Internal Server Error
```json
{
    "timestamp": "2026-01-23T16:00:00",
    "status": 500,
    "error": "Internal Server Error",
    "message": "Failed to connect to CodeExecutionService"
}
```
