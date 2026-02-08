package com.hrishabh.algocracksubmissionservice.adapter;

import com.hrishabh.algocracksubmissionservice.dto.internal.BatchExecutionResult;
import com.hrishabh.algocracksubmissionservice.dto.internal.CodeBundle;

/**
 * Abstraction layer for code execution.
 * Decouples the Submission Service from any specific execution engine (CXE).
 * 
 * All execution services (oracle, user code) should use this interface,
 * never CXE DTOs directly.
 */
public interface ExecutionAdapter {

    /**
     * Execute code with all testcases in a single batch.
     * Implementation handles translation to/from engine-specific formats.
     * 
     * @param codeBundle The code and testcases to execute
     * @return The execution result with outputs for each testcase
     */
    BatchExecutionResult execute(CodeBundle codeBundle);
}
