package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.ImportDeclaration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves JDK call targets from explicit static/import-qualified names only.
 */
public final class JdkCallTargetResolver {

    private static final Set<String> ALLOWED_CONCRETE_JDK_RECEIVERS = Set.of(
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.ArrayList",
            "java.util.PriorityQueue",
            "java.lang.String",
            "java.lang.StringBuilder");

    private static final Set<String> JDK_INTERFACE_SIMPLE_NAMES = Set.of(
            "List", "Map", "Set", "Collection", "Iterable", "Queue", "Deque", "SortedMap", "SortedSet");

    private static final Map<String, String> SIMPLE_STATIC_JDK = Map.ofEntries(
            Map.entry("Arrays", "java.util.Arrays"),
            Map.entry("Collections", "java.util.Collections"),
            Map.entry("Integer", "java.lang.Integer"),
            Map.entry("String", "java.lang.String"));

    private JdkCallTargetResolver() {
    }

    public static Optional<JdkCallTarget> resolve(MethodCallExpr call, CompilationUnit unit) {
        if (call.getScope().isEmpty()) {
            return Optional.empty();
        }
        Expression scope = call.getScope().get();
        if (scope instanceof NameExpr nameExpr) {
            String simple = nameExpr.getNameAsString();
            Optional<String> imported = resolveImport(unit, simple);
            if (imported.isPresent()) {
                return Optional.of(new JdkCallTarget(imported.get(), true));
            }
            if (SIMPLE_STATIC_JDK.containsKey(simple)) {
                return Optional.of(new JdkCallTarget(SIMPLE_STATIC_JDK.get(simple), true));
            }
            return Optional.empty();
        }
        if (scope instanceof FieldAccessExpr fieldAccess) {
            if ("System".equals(fieldAccess.getScope().toString()) && "out".equals(fieldAccess.getNameAsString())) {
                return Optional.empty();
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static Optional<JdkCallTarget> resolveInstanceCall(
            MethodCallExpr call,
            Map<String, String> declaredParamTypes,
            CompilationUnit unit) {
        if (call.getScope().isEmpty()) {
            return Optional.empty();
        }
        Expression scope = call.getScope().get();
        if (scope instanceof NameExpr name) {
            String type = declaredParamTypes.get(name.getNameAsString());
            if (type == null) {
                return Optional.empty();
            }
            String normalized = normalizeJavaType(type);
            if (isUserDefinedType(unit, normalized)) {
                return Optional.empty();
            }
            if (isJdkInterfaceType(normalized)) {
                return Optional.empty();
            }
            if (isAllowedConcreteJdkReceiver(normalized)) {
                return Optional.of(new JdkCallTarget(normalized, false));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static boolean isUserDefinedType(CompilationUnit unit, String normalizedType) {
        if (normalizedType.startsWith("java.") || normalizedType.startsWith("javax.")) {
            return false;
        }
        String simple = normalizedType.contains(".")
                ? normalizedType.substring(normalizedType.lastIndexOf('.') + 1)
                : normalizedType;
        return unit.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(c -> c.getNameAsString().equals(simple));
    }

    private static Optional<String> resolveImport(CompilationUnit unit, String simpleName) {
        Map<String, String> imports = new HashMap<>();
        for (ImportDeclaration importDeclaration : unit.getImports()) {
            if (importDeclaration.isStatic()) {
                continue;
            }
            String name = importDeclaration.getNameAsString();
            if (name.endsWith("." + simpleName)) {
                imports.put(simpleName, name);
            }
            if (!name.contains(".")) {
                imports.putIfAbsent(simpleName, name);
            }
        }
        return Optional.ofNullable(imports.get(simpleName));
    }

    private static boolean isAllowedConcreteJdkReceiver(String normalized) {
        if (ALLOWED_CONCRETE_JDK_RECEIVERS.contains(normalized)) {
            return true;
        }
        return normalized.startsWith("java.util.")
                && !isJdkInterfaceType(normalized)
                && normalized.endsWith("Queue");
    }

    private static boolean isJdkInterfaceType(String normalized) {
        if (JDK_INTERFACE_SIMPLE_NAMES.contains(normalized)) {
            return true;
        }
        String simple = normalized.contains(".")
                ? normalized.substring(normalized.lastIndexOf('.') + 1)
                : normalized;
        return JDK_INTERFACE_SIMPLE_NAMES.contains(simple);
    }

    static String normalizeJavaType(String type) {
        String t = type.trim();
        if (t.endsWith("[]")) {
            return "array";
        }
        if (t.contains("<")) {
            t = t.substring(0, t.indexOf('<'));
        }
        if (!t.contains(".") && !SIMPLE_STATIC_JDK.containsKey(t)) {
            return t;
        }
        if (SIMPLE_STATIC_JDK.containsKey(t)) {
            return SIMPLE_STATIC_JDK.get(t);
        }
        return t;
    }
}
