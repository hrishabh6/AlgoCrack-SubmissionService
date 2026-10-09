package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Indexes user-defined methods by {@link MethodIdentity} and supports lookup by declaring type + name.
 */
public final class UserMethodIndex {

    private final Map<MethodIdentity, MethodDeclaration> byIdentity = new HashMap<>();
    private final Map<String, List<MethodIdentity>> byTypeAndName = new HashMap<>();

    private UserMethodIndex() {
    }

    public static UserMethodIndex build(CompilationUnit unit) {
        UserMethodIndex index = new UserMethodIndex();
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            String typeName = type.getNameAsString();
            for (MethodDeclaration method : type.getMethods()) {
                if (method.isAbstract()) {
                    continue;
                }
                MethodIdentity id = MethodIdentity.of(method, typeName);
                index.byIdentity.put(id, method);
                index.byTypeAndName
                        .computeIfAbsent(typeName + "#" + method.getNameAsString(), k -> new ArrayList<>())
                        .add(id);
            }
        }
        return index;
    }

    public Optional<MethodDeclaration> declaration(MethodIdentity identity) {
        return Optional.ofNullable(byIdentity.get(identity));
    }

    public List<MethodIdentity> candidatesInType(String declaringTypeName, String methodName) {
        return List.copyOf(byTypeAndName.getOrDefault(declaringTypeName + "#" + methodName, List.of()));
    }

    public boolean contains(MethodIdentity identity) {
        return byIdentity.containsKey(identity);
    }

    public Iterable<MethodIdentity> identities() {
        return byIdentity.keySet();
    }
}
