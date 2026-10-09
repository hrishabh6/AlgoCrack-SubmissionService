package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexityExprSimplifierPropertyTest {

    @Test
    void productCommutativeCanonical() {
        ComplexityExpr ab = new ComplexityExpr.Product(List.of(ComplexityExpr.var("m"), ComplexityExpr.var("n")));
        ComplexityExpr ba = new ComplexityExpr.Product(List.of(ComplexityExpr.var("n"), ComplexityExpr.var("m")));
        assertEquals(
                ComplexityExprSimplifier.normalizeShape(ComplexityExprSimplifier.simplify(ab)),
                ComplexityExprSimplifier.normalizeShape(ComplexityExprSimplifier.simplify(ba)));
    }

    @Test
    void sumIndependentVariablesPreserved() {
        ComplexityExpr sum = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                ComplexityExpr.var("n"), ComplexityExpr.var("m"))));
        assertEquals("O(n + m)", ComplexityExprSimplifier.toBigOString(sum));
    }

    @Test
    void productSquareAndMixedPower() {
        assertEquals("O(n²)", ComplexityExprSimplifier.toBigOString(ComplexityExprSimplifier.simplify(
                new ComplexityExpr.Product(List.of(ComplexityExpr.var("n"), ComplexityExpr.var("n"))))));
        assertEquals("O(n² * m)", ComplexityExprSimplifier.toBigOString(ComplexityExprSimplifier.simplify(
                new ComplexityExpr.Product(List.of(
                        ComplexityExpr.var("n"), ComplexityExpr.var("n"), ComplexityExpr.var("m"))))));
    }

    @Test
    void sumDominanceWithinSameVariable() {
        assertEquals("O(n²)", ComplexityExprSimplifier.toBigOString(ComplexityExprSimplifier.simplify(
                new ComplexityExpr.Sum(List.of(
                        ComplexityExpr.var("n"),
                        new ComplexityExpr.Power("n", 2))))));
    }

    @Test
    void logOfProduct() {
        ComplexityExpr expr = new ComplexityExpr.Log(new ComplexityExpr.Product(List.of(
                ComplexityExpr.var("n"), ComplexityExpr.var("m"))));
        String s = ComplexityExprSimplifier.toExpressionString(ComplexityExprSimplifier.simplify(expr));
        assertTrue(s.contains("log") && s.contains("n") && s.contains("m"));
    }

    @Test
    void displayOrderIsTotal() {
        var cmp = ComplexityExprSimplifier.displayOrderComparator();
        assertTrue(cmp.compare("rows", "result") != 0);
        assertTrue(cmp.compare("foo", "bar") != 0);
        assertEquals(0, cmp.compare("x", "x"));
    }

    @Test
    void symbolPreservationInReferencedSet() {
        ComplexityExpr expr = ComplexityExprSimplifier.simplify(new ComplexityExpr.Product(List.of(
                ComplexityExpr.var("rows"), ComplexityExpr.var("result"))));
        Set<String> refs = ComplexityExprSimplifier.referencedVariableNames(expr);
        assertTrue(refs.contains("rows") && refs.contains("result"));
    }
}
