package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;

import java.util.EnumSet;

public record JdkKnowledgeEntry(
        JdkOperationIdentity operation,
        ComplexityBoundBasis boundBasis,
        ComplexityExpr timeExpression,
        ComplexityExpr allocationExpression,
        EnumSet<JdkTemplateVariable> requiredRoles,
        boolean requiresComparatorProof,
        String note) {

    /** Legacy display key for findings. */
    public String pattern() {
        return operation.qualifiedOwner() + "." + operation.methodName();
    }
}
