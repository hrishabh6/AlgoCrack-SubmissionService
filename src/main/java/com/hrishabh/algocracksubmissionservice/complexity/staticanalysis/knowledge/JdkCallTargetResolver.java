package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
 * Resolves JDK call targets with source shadowing safety (Batch 3).
 */
public final class JdkCallTargetResolver {

    private static final Set<String> ALLOWED_CONCRETE_JDK_RECEIVERS = Set.of(
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.PriorityQueue",
            "java.util.ArrayDeque",
            "java.util.TreeMap",
            "java.util.TreeSet",
            "java.lang.String",
            "java.lang.StringBuilder");

    private static final Set<String> JDK_INTERFACE_SIMPLE_NAMES = Set.of(
            "List", "Map", "Set", "Collection", "Iterable", "Queue", "Deque", "SortedMap", "SortedSet");

    private static final Map<String, String> SIMPLE_STATIC_JDK = Map.ofEntries(
            Map.entry("Arrays", "java.util.Arrays"),
            Map.entry("Collections", "java.util.Collections"),
            Map.entry("Integer", "java.lang.Integer"),
            Map.entry("String", "java.lang.String"),
            Map.entry("System", "java.lang.System"));

    private JdkCallTargetResolver() {
    }

    public static Optional<JdkCallTarget> resolveStaticCall(MethodCallExpr call, CompilationUnit unit) {
        if (call.getScope().isEmpty()) {
            return Optional.empty();
        }
        Expression scope = call.getScope().get();
        Optional<String> qualified = qualifiedTypeFromScope(scope, unit);
        if (qualified.isEmpty()) {
            return Optional.empty();
        }
        String owner = qualified.get();
        if (!owner.startsWith("java.") && !owner.startsWith("javax.")) {
            return Optional.empty();
        }
        if (isSourceDefinedSimpleName(unit, simpleNameFromQualified(owner))) {
            return Optional.empty();
        }
        return Optional.of(new JdkCallTarget(owner, true));
    }

    public static Optional<JdkCallTarget> resolveInstanceCall(
            MethodCallExpr call,
            Map<String, String> declaredParamTypes,
            Map<String, String> localNameToType,
            Map<String, String> localJdkReceiverTypes,
            CompilationUnit unit) {
        if (call.getScope().isEmpty()) {
            return Optional.empty();
        }
        Expression scope = call.getScope().get();
        if (!(scope instanceof NameExpr name)) {
            return Optional.empty();
        }
        String var = name.getNameAsString();
        String type = localJdkReceiverTypes.get(var);
        if (type == null) {
            type = declaredParamTypes.get(var);
        }
        if (type == null) {
            type = localNameToType.get(var);
        }
        if (type == null) {
            return Optional.empty();
        }
        String normalized = normalizeJavaType(type, unit);
        if (isSourceDefinedSimpleName(unit, simpleNameFromQualified(normalized))) {
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

    /** @deprecated use {@link #resolveStaticCall} */
    public static Optional<JdkCallTarget> resolve(MethodCallExpr call, CompilationUnit unit) {
        return resolveStaticCall(call, unit);
    }

    public static boolean isSourceDefinedSimpleName(CompilationUnit unit, String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return false;
        }
        return unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(c -> simpleName.equals(c.getNameAsString()));
    }

    private static Optional<String> qualifiedTypeFromScope(Expression scope, CompilationUnit unit) {
        if (scope instanceof NameExpr nameExpr) {
            String simple = nameExpr.getNameAsString();
            if (isSourceDefinedSimpleName(unit, simple)) {
                return Optional.empty();
            }
            Optional<String> imported = resolveImport(unit, simple);
            if (imported.isPresent()) {
                return imported;
            }
            if (SIMPLE_STATIC_JDK.containsKey(simple)) {
                return Optional.of(SIMPLE_STATIC_JDK.get(simple));
            }
            return Optional.empty();
        }
        if (scope instanceof FieldAccessExpr fieldAccess) {
            return qualifiedNameFromFieldAccess(fieldAccess);
        }
        return Optional.empty();
    }

    private static Optional<String> qualifiedNameFromFieldAccess(FieldAccessExpr fieldAccess) {
        String name = fieldAccess.getNameAsString();
        Expression scope = fieldAccess.getScope();
        if (scope instanceof NameExpr nameExpr) {
            return Optional.of(nameExpr.getNameAsString() + "." + name);
        }
        if (scope instanceof FieldAccessExpr inner) {
            return qualifiedNameFromFieldAccess(inner).map(prefix -> prefix + "." + name);
        }
        return Optional.empty();
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
        String simple = simpleNameFromQualified(normalized);
        return JDK_INTERFACE_SIMPLE_NAMES.contains(simple);
    }

    static String normalizeJavaType(String type, CompilationUnit unit) {
        String t = type.trim();
        if (t.endsWith("[]")) {
            return JdkOperationIdentity.eraseType(t);
        }
        if (t.contains("<")) {
            t = t.substring(0, t.indexOf('<'));
        }
        if (!t.contains(".")) {
            if (SIMPLE_STATIC_JDK.containsKey(t)) {
                return SIMPLE_STATIC_JDK.get(t);
            }
            if (isSourceDefinedSimpleName(unit, t)) {
                return t;
            }
            return t;
        }
        return t;
    }

    static String simpleNameFromQualified(String normalized) {
        if (normalized == null) {
            return "";
        }
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }
}
