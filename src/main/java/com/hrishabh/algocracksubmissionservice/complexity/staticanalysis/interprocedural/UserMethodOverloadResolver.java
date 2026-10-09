package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Conservative overload resolution for unqualified calls within a declaring type.
 */
public final class UserMethodOverloadResolver {

    public enum ResolutionOutcome {
        RESOLVED,
        AMBIGUOUS,
        UNRESOLVED
    }

    public record Resolution(ResolutionOutcome outcome, MethodIdentity identity, MethodDeclaration declaration) {
        public static Resolution ambiguous() {
            return new Resolution(ResolutionOutcome.AMBIGUOUS, null, null);
        }

        public static Resolution unresolved() {
            return new Resolution(ResolutionOutcome.UNRESOLVED, null, null);
        }

        public static Resolution resolved(MethodIdentity identity, MethodDeclaration declaration) {
            return new Resolution(ResolutionOutcome.RESOLVED, identity, declaration);
        }
    }

    private UserMethodOverloadResolver() {
    }

    public static Resolution resolveUnqualifiedCall(
            MethodCallExpr call,
            String declaringTypeName,
            UserMethodIndex index,
            Map<String, String> localNameToType,
            Map<String, String> parameterNameToType) {
        String methodName = call.getNameAsString();
        List<MethodIdentity> candidates = index.candidatesInType(declaringTypeName, methodName);
        if (candidates.isEmpty()) {
            return Resolution.unresolved();
        }
        List<MethodIdentity> arityMatches = candidates.stream()
                .filter(c -> c.parameterTypes().size() == call.getArguments().size())
                .toList();
        if (arityMatches.isEmpty()) {
            return Resolution.unresolved();
        }
        if (arityMatches.size() == 1) {
            return resolveSingleArityCandidate(call, arityMatches.get(0), index, localNameToType, parameterNameToType);
        }
        List<MethodIdentity> applicable = new ArrayList<>();
        for (MethodIdentity candidate : arityMatches) {
            if (argumentsMatchParameters(call.getArguments(), candidate.parameterTypes(), localNameToType, parameterNameToType)) {
                applicable.add(candidate);
            }
        }
        if (applicable.isEmpty()) {
            return Resolution.unresolved();
        }
        if (applicable.size() == 1) {
            MethodIdentity id = applicable.getFirst();
            return Resolution.resolved(id, index.declaration(id).orElseThrow());
        }
        MethodIdentity mostSpecific = selectMostSpecific(applicable, call.getArguments(), localNameToType, parameterNameToType);
        if (mostSpecific != null) {
            return Resolution.resolved(mostSpecific, index.declaration(mostSpecific).orElseThrow());
        }
        return Resolution.ambiguous();
    }

    private static Resolution resolveSingleArityCandidate(
            MethodCallExpr call,
            MethodIdentity candidate,
            UserMethodIndex index,
            Map<String, String> localNameToType,
            Map<String, String> parameterNameToType) {
        List<String> parameterTypes = candidate.parameterTypes();
        boolean anyInferred = false;
        boolean allInferred = true;
        for (int i = 0; i < call.getArguments().size(); i++) {
            Optional<String> argType = inferArgumentType(call.getArgument(i), localNameToType, parameterNameToType);
            if (argType.isEmpty()) {
                allInferred = false;
                continue;
            }
            anyInferred = true;
            if (!isConvertible(argType.get(), parameterTypes.get(i))) {
                return Resolution.unresolved();
            }
        }
        if (anyInferred && allInferred) {
            return Resolution.resolved(candidate, index.declaration(candidate).orElseThrow());
        }
        if (!anyInferred) {
            return Resolution.resolved(candidate, index.declaration(candidate).orElseThrow());
        }
        return Resolution.unresolved();
    }

    private static boolean argumentsMatchParameters(
            List<Expression> arguments,
            List<String> parameterTypes,
            Map<String, String> localNameToType,
            Map<String, String> parameterNameToType) {
        for (int i = 0; i < arguments.size(); i++) {
            Optional<String> argType = inferArgumentType(arguments.get(i), localNameToType, parameterNameToType);
            if (argType.isEmpty()) {
                return false;
            }
            if (!isConvertible(argType.get(), parameterTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static MethodIdentity selectMostSpecific(
            List<MethodIdentity> applicable,
            List<Expression> arguments,
            Map<String, String> localNameToType,
            Map<String, String> parameterNameToType) {
        MethodIdentity best = null;
        for (MethodIdentity candidate : applicable) {
            if (best == null) {
                best = candidate;
                continue;
            }
            if (isStrictlyMoreSpecific(candidate, best, arguments, localNameToType, parameterNameToType)) {
                best = candidate;
            } else if (isStrictlyMoreSpecific(best, candidate, arguments, localNameToType, parameterNameToType)) {
                // keep best
            } else {
                return null;
            }
        }
        return best;
    }

    private static boolean isStrictlyMoreSpecific(
            MethodIdentity a,
            MethodIdentity b,
            List<Expression> arguments,
            Map<String, String> localNameToType,
            Map<String, String> parameterNameToType) {
        boolean anyStrict = false;
        for (int i = 0; i < arguments.size(); i++) {
            Optional<String> argType = inferArgumentType(arguments.get(i), localNameToType, parameterNameToType);
            if (argType.isEmpty()) {
                return false;
            }
            String paramA = a.parameterTypes().get(i);
            String paramB = b.parameterTypes().get(i);
            if (paramA.equals(paramB)) {
                continue;
            }
            if (isConvertible(argType.get(), paramA) && isConvertible(paramA, paramB) && !paramA.equals(paramB)) {
                anyStrict = true;
            } else if (isConvertible(argType.get(), paramB) && isConvertible(paramB, paramA) && !paramA.equals(paramB)) {
                return false;
            } else {
                return false;
            }
        }
        return anyStrict;
    }

    private static Optional<String> inferArgumentType(
            Expression argument,
            Map<String, String> localNameToType,
            Map<String, String> parameterNameToType) {
        if (argument instanceof IntegerLiteralExpr) {
            return Optional.of("int");
        }
        if (argument instanceof StringLiteralExpr) {
            return Optional.of("String");
        }
        if (argument instanceof NameExpr name) {
            String n = name.getNameAsString();
            if (localNameToType.containsKey(n)) {
                return Optional.of(MethodIdentity.eraseType(localNameToType.get(n)));
            }
            if (parameterNameToType.containsKey(n)) {
                return Optional.of(MethodIdentity.eraseType(parameterNameToType.get(n)));
            }
            return Optional.empty();
        }
        if (argument instanceof BinaryExpr binary) {
            return switch (binary.getOperator()) {
                case PLUS, MINUS, MULTIPLY, DIVIDE, REMAINDER -> {
                    Optional<String> left = inferArgumentType(binary.getLeft(), localNameToType, parameterNameToType);
                    Optional<String> right = inferArgumentType(binary.getRight(), localNameToType, parameterNameToType);
                    if (left.isPresent() && right.isPresent() && INTEGRAL.contains(left.get()) && INTEGRAL.contains(right.get())) {
                        yield Optional.of("int");
                    }
                    if (left.isPresent() && INTEGRAL.contains(left.get())) {
                        yield left;
                    }
                    yield Optional.empty();
                }
                default -> Optional.empty();
            };
        }
        if (argument instanceof com.github.javaparser.ast.expr.ArrayCreationExpr array) {
            return Optional.of(array.getElementType().asString() + "[]");
        }
        return Optional.empty();
    }

    private static final Set<String> INTEGRAL = Set.of("byte", "short", "int", "long", "char");
    private static final Set<String> NUMERIC = Set.of("byte", "short", "int", "long", "char", "float", "double");

    private static boolean isConvertible(String from, String to) {
        String f = normalize(from);
        String t = normalize(to);
        if (f.equals(t)) {
            return true;
        }
        if (INTEGRAL.contains(f) && INTEGRAL.contains(t)) {
            return integralRank(f) <= integralRank(t);
        }
        if (NUMERIC.contains(f) && NUMERIC.contains(t)) {
            return numericRank(f) <= numericRank(t);
        }
        if (f.endsWith("[]") && t.endsWith("[]")) {
            return isConvertible(f.substring(0, f.length() - 2), t.substring(0, t.length() - 2));
        }
        return false;
    }

    private static String normalize(String type) {
        return type == null ? "unknown" : type.trim();
    }

    private static int integralRank(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "byte" -> 1;
            case "short" -> 2;
            case "char" -> 3;
            case "int" -> 4;
            case "long" -> 5;
            default -> 99;
        };
    }

    private static int numericRank(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "byte" -> 1;
            case "short" -> 2;
            case "char" -> 3;
            case "int" -> 4;
            case "long" -> 5;
            case "float" -> 6;
            case "double" -> 7;
            default -> 99;
        };
    }
}
