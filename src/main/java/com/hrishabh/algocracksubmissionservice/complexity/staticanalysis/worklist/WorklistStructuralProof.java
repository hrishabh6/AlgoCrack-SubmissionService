package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.worklist;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Narrow structural proof for worklist-driven loops (Batch 5).
 * Ordinary counted loops stay on {@code ConservativeLoopBoundProof}.
 */
public final class WorklistStructuralProof {

    private static final Set<String> SUPPORTED_CONTAINERS = Set.of(
            "java.util.PriorityQueue",
            "java.util.ArrayDeque");
    private static final Set<String> CONSUME = Set.of("poll", "pop", "remove");
    private static final Set<String> ADMIT = Set.of("offer", "add", "push");

    private WorklistStructuralProof() {
    }

    public record Proof(String containerName, ComplexityExpr universe, String evidence) {
    }

    public record Attempt(boolean recognized, Optional<Proof> proof, StaticAnalysisReasonCode failure) {
        public static Attempt notWorklist() {
            return new Attempt(false, Optional.empty(), null);
        }

        public static Attempt failed(StaticAnalysisReasonCode code) {
            return new Attempt(true, Optional.empty(), code);
        }

        public static Attempt proven(Proof proof) {
            return new Attempt(true, Optional.of(proof), null);
        }
    }

    public interface Lookup {
        Optional<ComplexityExpr> sizeOf(Expression expression);

        Optional<String> concreteType(String localName);
    }

    public static Attempt attempt(WhileStmt loop, Lookup lookup) {
        Optional<String> container = containerFromCondition(loop.getCondition());
        if (container.isEmpty()) {
            return Attempt.notWorklist();
        }
        String containerName = container.get();
        Optional<String> type = lookup.concreteType(containerName);
        if (type.isEmpty() || !SUPPORTED_CONTAINERS.contains(type.get())) {
            return Attempt.failed(StaticAnalysisReasonCode.WORKLIST_CONTAINER_UNRESOLVED);
        }
        if (!(loop.getBody() instanceof BlockStmt body)) {
            return Attempt.failed(StaticAnalysisReasonCode.WORKLIST_CONSUMPTION_NOT_PROVEN);
        }
        if (!unconditionalConsumption(body, containerName)) {
            return Attempt.failed(StaticAnalysisReasonCode.WORKLIST_CONSUMPTION_NOT_PROVEN);
        }
        if (opaqueMutation(body, containerName)) {
            return Attempt.failed(StaticAnalysisReasonCode.WORKLIST_MUTATION_UNRESOLVED);
        }
        Optional<Admission> admission = findAdmission(body, containerName);
        if (admission.isEmpty()) {
            return Attempt.failed(StaticAnalysisReasonCode.WORKLIST_ADMISSION_NOT_BOUNDED);
        }
        Admission guard = admission.get();
        if (!offersMatchAdmission(body, containerName, guard)) {
            return Attempt.failed(StaticAnalysisReasonCode.ADMISSION_STATE_IDENTITY_UNRESOLVED);
        }
        Optional<MethodDeclaration> method = loop.findAncestor(MethodDeclaration.class);
        if (method.isEmpty()) {
            return Attempt.failed(StaticAnalysisReasonCode.STRUCTURAL_PROOF_INCOMPLETE);
        }
        if (markerResets(method.get(), guard.markerName())) {
            return Attempt.failed(StaticAnalysisReasonCode.VISITED_MARKER_NOT_MONOTONIC);
        }
        if (opaqueMutation(method.get(), guard.markerName()) || opaqueMutation(body, guard.markerName())) {
            return Attempt.failed(StaticAnalysisReasonCode.WORKLIST_MUTATION_UNRESOLVED);
        }
        Optional<ComplexityExpr> universe = universeOf(method.get(), guard.markerName(), lookup);
        if (universe.isEmpty() || universe.get() instanceof ComplexityExpr.Unknown) {
            return Attempt.failed(StaticAnalysisReasonCode.WORKLIST_STATE_UNIVERSE_UNRESOLVED);
        }
        if (!seedsBounded(method.get(), loop, containerName, lookup)) {
            return Attempt.failed(StaticAnalysisReasonCode.WORKLIST_ADMISSION_NOT_BOUNDED);
        }
        if (!neighborEnumerationBounded(body, method.get())) {
            return Attempt.failed(StaticAnalysisReasonCode.NEIGHBOR_BOUND_UNRESOLVED);
        }
        ComplexityExpr simplified = ComplexityExprSimplifier.simplify(universe.get());
        String evidence = "state universe=" + ComplexityExprSimplifier.toExpressionString(simplified)
                + "; marker=" + guard.markerName()
                + "; container=" + type.get();
        return Attempt.proven(new Proof(containerName, simplified, evidence));
    }

    private static Optional<String> containerFromCondition(Expression condition) {
        if (condition instanceof UnaryExpr unary
                && unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT
                && unary.getExpression() instanceof MethodCallExpr call
                && "isEmpty".equals(call.getNameAsString())
                && call.getScope().isPresent()
                && call.getScope().get() instanceof NameExpr name) {
            return Optional.of(name.getNameAsString());
        }
        if (condition instanceof BinaryExpr binary
                && binary.getOperator() == BinaryExpr.Operator.GREATER
                && binary.getLeft() instanceof MethodCallExpr call
                && "size".equals(call.getNameAsString())
                && call.getScope().isPresent()
                && call.getScope().get() instanceof NameExpr name
                && binary.getRight().isIntegerLiteralExpr()
                && "0".equals(binary.getRight().asIntegerLiteralExpr().getValue())) {
            return Optional.of(name.getNameAsString());
        }
        return Optional.empty();
    }

    private static boolean unconditionalConsumption(BlockStmt body, String container) {
        for (Statement statement : body.getStatements()) {
            if (statement instanceof IfStmt) {
                continue;
            }
            if (containsConsumeCall(statement, container)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsConsumeCall(Node node, String container) {
        return node.findAll(MethodCallExpr.class).stream().anyMatch(call -> isContainerCall(call, container, CONSUME)
                && call.getArguments().isEmpty());
    }

    private static boolean opaqueMutation(Node node, String name) {
        for (MethodCallExpr call : node.findAll(MethodCallExpr.class)) {
            boolean receiver = call.getScope().isPresent()
                    && call.getScope().get() instanceof NameExpr scope
                    && name.equals(scope.getNameAsString());
            if (receiver) {
                continue;
            }
            for (Expression argument : call.getArguments()) {
                if (argument instanceof NameExpr arg && name.equals(arg.getNameAsString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private record Admission(String markerName, List<String> indices) {
    }

    private static Optional<Admission> findAdmission(BlockStmt body, String container) {
        for (IfStmt ifStmt : body.findAll(IfStmt.class)) {
            Optional<ArrayAccessExpr> guard = negatedMarker(ifStmt.getCondition());
            if (guard.isEmpty()) {
                continue;
            }
            ArrayAccessExpr access = guard.get();
            Optional<String> marker = markerBase(access);
            if (marker.isEmpty()) {
                continue;
            }
            List<String> indices = indexTexts(access);
            if (!assignsTrue(ifStmt.getThenStmt(), marker.get(), indices)) {
                continue;
            }
            boolean offers = ifStmt.getThenStmt().findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> isContainerCall(call, container, ADMIT) && coversIndices(call, indices));
            if (offers) {
                return Optional.of(new Admission(marker.get(), indices));
            }
        }
        return Optional.empty();
    }

    private static boolean offersMatchAdmission(BlockStmt body, String container, Admission guard) {
        List<MethodCallExpr> offers = body.findAll(MethodCallExpr.class).stream()
                .filter(call -> isContainerCall(call, container, ADMIT))
                .toList();
        if (offers.isEmpty()) {
            return false;
        }
        for (MethodCallExpr offer : offers) {
            Optional<IfStmt> parent = offer.findAncestor(IfStmt.class);
            if (parent.isEmpty()) {
                return false;
            }
            Optional<ArrayAccessExpr> negated = negatedMarker(parent.get().getCondition());
            if (negated.isEmpty()) {
                return false;
            }
            Optional<String> marker = markerBase(negated.get());
            if (marker.isEmpty() || !guard.markerName().equals(marker.get())) {
                return false;
            }
            if (!indexTexts(negated.get()).equals(guard.indices()) || !coversIndices(offer, guard.indices())) {
                return false;
            }
            if (!assignsTrue(parent.get().getThenStmt(), guard.markerName(), guard.indices())) {
                return false;
            }
        }
        return true;
    }

    private static Optional<ArrayAccessExpr> negatedMarker(Expression condition) {
        if (condition instanceof UnaryExpr unary
                && unary.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT
                && unary.getExpression() instanceof ArrayAccessExpr access) {
            return Optional.of(access);
        }
        if (condition instanceof BinaryExpr binary
                && (binary.getOperator() == BinaryExpr.Operator.AND || binary.getOperator() == BinaryExpr.Operator.OR)) {
            Optional<ArrayAccessExpr> left = negatedMarker(binary.getLeft());
            if (left.isPresent()) {
                return left;
            }
            return negatedMarker(binary.getRight());
        }
        if (condition instanceof com.github.javaparser.ast.expr.EnclosedExpr enclosed) {
            return negatedMarker(enclosed.getInner());
        }
        return Optional.empty();
    }

    private static boolean assignsTrue(Statement statement, String marker, List<String> indices) {
        return statement.findAll(AssignExpr.class).stream().anyMatch(assign -> {
            if (!(assign.getTarget() instanceof ArrayAccessExpr access)) {
                return false;
            }
            Optional<String> base = markerBase(access);
            if (base.isEmpty() || !marker.equals(base.get()) || !indexTexts(access).equals(indices)) {
                return false;
            }
            return assign.getValue() instanceof BooleanLiteralExpr literal && literal.getValue();
        });
    }

    private static boolean coversIndices(MethodCallExpr offer, List<String> indices) {
        List<String> direct = offer.getArguments().stream().map(Expression::toString).toList();
        if (direct.containsAll(indices)) {
            return true;
        }
        for (Expression argument : offer.getArguments()) {
            if (argument instanceof ObjectCreationExpr creation) {
                List<String> created = creation.getArguments().stream().map(Expression::toString).toList();
                if (created.containsAll(indices)) {
                    return true;
                }
            }
            if (argument instanceof ArrayCreationExpr array && array.getInitializer().isPresent()) {
                List<String> created = array.getInitializer().get().getValues().stream().map(Expression::toString).toList();
                if (created.containsAll(indices)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<String> markerBase(ArrayAccessExpr access) {
        Expression current = access;
        while (current instanceof ArrayAccessExpr array) {
            current = array.getName();
        }
        if (current instanceof NameExpr name) {
            return Optional.of(name.getNameAsString());
        }
        return Optional.empty();
    }

    private static List<String> indexTexts(ArrayAccessExpr access) {
        List<String> indices = new ArrayList<>();
        Expression current = access;
        while (current instanceof ArrayAccessExpr array) {
            indices.add(array.getIndex().toString());
            current = array.getName();
        }
        java.util.Collections.reverse(indices);
        return indices;
    }

    private static boolean markerResets(MethodDeclaration method, String marker) {
        return method.findAll(AssignExpr.class).stream().anyMatch(assign -> {
            if (!(assign.getTarget() instanceof ArrayAccessExpr access)) {
                return false;
            }
            Optional<String> base = markerBase(access);
            if (base.isEmpty() || !marker.equals(base.get())) {
                return false;
            }
            return assign.getValue() instanceof BooleanLiteralExpr literal && !literal.getValue();
        });
    }

    private static Optional<ComplexityExpr> universeOf(MethodDeclaration method, String marker, Lookup lookup) {
        for (VariableDeclarator variable : method.findAll(VariableDeclarator.class)) {
            if (!marker.equals(variable.getNameAsString()) || variable.getInitializer().isEmpty()) {
                continue;
            }
            if (!(variable.getInitializer().get() instanceof ArrayCreationExpr array)) {
                return Optional.empty();
            }
            String element = array.getElementType().asString();
            if (!"boolean".equals(element)) {
                return Optional.empty();
            }
            List<ComplexityExpr> dimensions = new ArrayList<>();
            for (var level : array.getLevels()) {
                if (level.getDimension().isEmpty()) {
                    return Optional.empty();
                }
                Optional<ComplexityExpr> size = lookup.sizeOf(level.getDimension().get());
                if (size.isEmpty() || size.get() instanceof ComplexityExpr.Unknown) {
                    return Optional.empty();
                }
                dimensions.add(size.get());
            }
            if (dimensions.isEmpty()) {
                return Optional.empty();
            }
            if (dimensions.size() == 1) {
                return Optional.of(dimensions.getFirst());
            }
            return Optional.of(new ComplexityExpr.Product(dimensions));
        }
        return Optional.empty();
    }

    private static boolean seedsBounded(MethodDeclaration method, WhileStmt loop, String container, Lookup lookup) {
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            if (!isContainerCall(call, container, ADMIT)) {
                continue;
            }
            if (call.findAncestor(WhileStmt.class).filter(loop::equals).isPresent()) {
                continue;
            }
            if (call.findAncestor(ForEachStmt.class).isPresent()) {
                return false;
            }
            Optional<ForStmt> forStmt = call.findAncestor(ForStmt.class);
            if (forStmt.isPresent() && !countedSeed(forStmt.get(), lookup)) {
                return false;
            }
        }
        return true;
    }

    private static boolean countedSeed(ForStmt forStmt, Lookup lookup) {
        if (forStmt.getCompare().isEmpty() || !(forStmt.getCompare().get() instanceof BinaryExpr binary)) {
            return false;
        }
        if (binary.getOperator() != BinaryExpr.Operator.LESS && binary.getOperator() != BinaryExpr.Operator.LESS_EQUALS) {
            return false;
        }
        return lookup.sizeOf(binary.getRight()).filter(size -> !(size instanceof ComplexityExpr.Unknown)).isPresent();
    }

    private static boolean neighborEnumerationBounded(BlockStmt body, MethodDeclaration method) {
        for (ForEachStmt forEach : body.findAll(ForEachStmt.class)) {
            Expression iterable = forEach.getIterable();
            if (iterable instanceof NameExpr name && constantTable(method, name.getNameAsString())) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean constantTable(MethodDeclaration method, String name) {
        for (VariableDeclarator variable : method.findAll(VariableDeclarator.class)) {
            if (!name.equals(variable.getNameAsString()) || variable.getInitializer().isEmpty()) {
                continue;
            }
            Expression initializer = variable.getInitializer().get();
            if (initializer instanceof ArrayInitializerExpr) {
                return true;
            }
            if (initializer instanceof ArrayCreationExpr array && array.getInitializer().isPresent()) {
                return true;
            }
            return false;
        }
        return false;
    }

    private static boolean isContainerCall(MethodCallExpr call, String container, Set<String> names) {
        return names.contains(call.getNameAsString())
                && call.getScope().isPresent()
                && call.getScope().get() instanceof NameExpr name
                && container.equals(name.getNameAsString());
    }
}
