package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Stable user-method identity (Batch 2): declaring type, name, static flag, erased parameter types.
 */
public record MethodIdentity(
        String declaringTypeName,
        String methodName,
        boolean staticMethod,
        List<String> parameterTypes) {

    public MethodIdentity {
        Objects.requireNonNull(declaringTypeName, "declaringTypeName");
        Objects.requireNonNull(methodName, "methodName");
        parameterTypes = List.copyOf(parameterTypes);
    }

    public static MethodIdentity of(MethodDeclaration method, String declaringTypeName) {
        List<String> paramTypes = method.getParameters().stream()
                .map(p -> eraseType(p.getType().asString()))
                .collect(Collectors.toList());
        return new MethodIdentity(
                declaringTypeName,
                method.getNameAsString(),
                method.isStatic(),
                paramTypes);
    }

    public String cacheKey() {
        return declaringTypeName + "#" + methodName + "#" + (staticMethod ? "static" : "instance") + "#"
                + String.join(",", parameterTypes);
    }

    @Override
    public String toString() {
        return declaringTypeName + "#" + methodName + "(" + String.join(",", parameterTypes) + ")";
    }

    public static String eraseType(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String base = raw.trim();
        int generic = base.indexOf('<');
        if (generic >= 0) {
            base = base.substring(0, generic);
        }
        return base;
    }
}
