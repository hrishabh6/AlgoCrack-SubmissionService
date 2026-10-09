package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class JdkKnowledgeBase {

    public static final String VERSION = "jdk21-v4";

    private final List<JdkKnowledgeEntry> entries = JdkOperationCatalog.all();

    public String version() {
        return VERSION;
    }

    public Optional<JdkKnowledgeEntry> matchOperation(
            JdkCallTarget target,
            String methodName,
            List<String> inferredParameterTypes) {
        if (inferredParameterTypes.stream().anyMatch(t -> "unknown".equals(t))) {
            return Optional.empty();
        }
        for (JdkKnowledgeEntry entry : entries) {
            JdkOperationIdentity op = entry.operation();
            if (!op.qualifiedOwner().equalsIgnoreCase(target.qualifiedType())) {
                continue;
            }
            if (!op.methodName().equals(methodName)) {
                continue;
            }
            if (op.staticMethod() != target.staticCall()) {
                continue;
            }
            if (op.parameterTypes().size() != inferredParameterTypes.size()) {
                continue;
            }
            if (!signatureMatches(op.parameterTypes(), inferredParameterTypes)) {
                continue;
            }
            return Optional.of(entry);
        }
        return Optional.empty();
    }

    /** @deprecated Batch 2 arity-only matching */
    @Deprecated
    public Optional<JdkKnowledgeEntry> matchQualified(JdkCallTarget target, String methodName, int argumentCount) {
        for (JdkKnowledgeEntry entry : entries) {
            JdkOperationIdentity op = entry.operation();
            if (!op.qualifiedOwner().equalsIgnoreCase(target.qualifiedType())) {
                continue;
            }
            if (!op.methodName().equals(methodName)) {
                continue;
            }
            if (op.staticMethod() != target.staticCall()) {
                continue;
            }
            if (op.parameterTypes().size() == argumentCount) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static boolean signatureMatches(List<String> expected, List<String> inferred) {
        for (int i = 0; i < expected.size(); i++) {
            if (!typeCompatible(expected.get(i), inferred.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean typeCompatible(String expected, String inferred) {
        String e = expected.toLowerCase(Locale.ROOT);
        String i = inferred.toLowerCase(Locale.ROOT);
        if (e.equals(i)) {
            return true;
        }
        if (e.equals("object") || i.equals("object")) {
            return true;
        }
        if (simple(e).equals("list") && simple(i).equals("list")) {
            return true;
        }
        if (e.endsWith("[]") && i.endsWith("[]")) {
            return typeCompatible(e.substring(0, e.length() - 2), i.substring(0, i.length() - 2));
        }
        return false;
    }

    private static String simple(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }

    static ComplexityExpr tv(JdkTemplateVariable variable) {
        return ComplexityExpr.var(variable.symbol());
    }

    static ComplexityExpr logVar(JdkTemplateVariable variable) {
        return new ComplexityExpr.Log(tv(variable));
    }

    static JdkKnowledgeEntry op(
            String owner,
            String method,
            boolean staticMethod,
            List<String> params,
            ComplexityBoundBasis basis,
            ComplexityExpr time,
            ComplexityExpr alloc,
            EnumSet<JdkTemplateVariable> roles,
            boolean comparatorProof,
            boolean hashKeyProof,
            String note) {
        return new JdkKnowledgeEntry(
                new JdkOperationIdentity(owner, method, staticMethod, params),
                basis,
                time,
                alloc,
                roles,
                comparatorProof,
                hashKeyProof,
                note);
    }
}
