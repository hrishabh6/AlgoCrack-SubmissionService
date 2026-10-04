package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplexityExprSimplifierTest {

    @Test
    void sumPicksDominantTerm() {
        ComplexityExpr expr = new ComplexityExpr.Sum(java.util.List.of(
                ComplexityExpr.var("n"),
                ComplexityExpr.one()));
        assertEquals("O(n)", ComplexityExprSimplifier.toBigOString(expr));
    }

    @Test
    void productPreservesMultiVariable() {
        ComplexityExpr expr = new ComplexityExpr.Product(java.util.List.of(
                ComplexityExpr.var("n"), ComplexityExpr.var("m")));
        assertEquals("O(n * m)", ComplexityExprSimplifier.toBigOString(expr));
    }

    @Test
    void nLogNFormatting() {
        ComplexityExpr expr = ComplexityExpr.nLogN("n");
        assertEquals("O(n * log(n))", ComplexityExprSimplifier.toBigOString(expr));
    }
}
