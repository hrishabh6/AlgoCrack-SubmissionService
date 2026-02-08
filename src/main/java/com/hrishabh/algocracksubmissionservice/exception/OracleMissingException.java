package com.hrishabh.algocracksubmissionservice.exception;

/**
 * Exception thrown when a question does not have an oracle (reference solution).
 * All questions must have an oracle for judging to work.
 */
public class OracleMissingException extends RuntimeException {
    
    private final Long questionId;
    
    public OracleMissingException(Long questionId) {
        super("Oracle (reference solution) not found for question: " + questionId);
        this.questionId = questionId;
    }
    
    public OracleMissingException(Long questionId, String message) {
        super(message);
        this.questionId = questionId;
    }
    
    public Long getQuestionId() {
        return questionId;
    }
}
