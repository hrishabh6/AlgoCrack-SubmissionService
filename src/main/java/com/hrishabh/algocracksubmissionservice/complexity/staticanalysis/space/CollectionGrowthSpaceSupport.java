package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.space;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;

import java.util.Map;
import java.util.Optional;

/**
 * Narrow retained collection growth proofs (Batch 4).
 */
public final class CollectionGrowthSpaceSupport {

    private CollectionGrowthSpaceSupport() {
    }

    public static Optional<ComplexityExpr> retainedGrowthFromProvenForLoop(
            ForStmt forStmt,
            ComplexityExpr iterationBound,
            Map<String, String> localRawTypes) {
        if (iterationBound instanceof ComplexityExpr.Unknown) {
            return Optional.empty();
        }
        String listName = listTargetWithSingleAdd(forStmt.getBody());
        if (listName == null) {
            return Optional.empty();
        }
        String rawType = localRawTypes.getOrDefault(listName, "");
        if (!rawType.contains("ArrayList")) {
            return Optional.empty();
        }
        return Optional.of(iterationBound);
    }

    private static String listTargetWithSingleAdd(Statement body) {
        if (body == null) {
            return null;
        }
        String target = null;
        int addCount = 0;
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            if (!"add".equals(call.getNameAsString()) || call.getScope().isEmpty()) {
                continue;
            }
            Expression scope = call.getScope().get();
            if (scope instanceof NameExpr name) {
                if (target == null) {
                    target = name.getNameAsString();
                } else if (!target.equals(name.getNameAsString())) {
                    return null;
                }
                addCount++;
            }
        }
        return addCount == 1 ? target : null;
    }
}
