package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityConfidence;
import com.hrishabh.algocracksubmissionservice.complexity.model.ComplexityResultKind;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context.ParameterVariableMapper;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeEntry;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticFindingDraft;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class JavaStaticAnalyzer {

    public static final String ANALYZER_VERSION = "static-v1";
    public static final String CONFIDENCE_MODEL_VERSION = "static-confidence-v1";

    private final JdkKnowledgeBase knowledgeBase;

    public StaticAnalysisResult analyze(String source, QuestionMetadataApiDto metadata) {
        List<StaticFindingDraft> findings = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        try {
            CompilationUnit unit = StaticJavaParser.parse(source);
            Optional<MethodDeclaration> entry = resolveEntryMethod(unit, metadata);
            if (entry.isEmpty()) {
                return unsupported(findings, limitations, "ENTRY_METHOD_UNRESOLVED",
                        "Could not resolve entry method for complexity analysis");
            }
            MethodDeclaration entryMethod = entry.get();
            Map<String, String> variables = mergeVariables(metadata, entryMethod);
            AnalysisState state = new AnalysisState(findings, limitations, variables, knowledgeBase);

            detectMutableStatic(unit, state);

            Map<String, MethodDeclaration> methodIndex = indexMethods(unit);
            Map<String, ComplexityExpr> memo = new HashMap<>();
            ComplexityExpr time = analyzeBlock(entryMethod.getBody().orElse(new BlockStmt()), state, methodIndex, memo, entryMethod.getNameAsString(), 0);
            ComplexityExpr space = analyzeAuxiliarySpace(entryMethod, unit, state, methodIndex, memo);

            ComplexityBoundBasis timeBasis = state.worstTimeBasis();
            ComplexityResultKind kind = classify(time, state);
            ComplexityConfidence timeConfidence = confidenceFor(state, time);
            ComplexityConfidence spaceConfidence = space instanceof ComplexityExpr.Unknown ? null : confidenceFor(state, space);

            if (kind == ComplexityResultKind.STATIC_ONLY) {
                limitations.add("PROFILE_UNAVAILABLE");
            }

            List<String> evidence = buildEvidence(state, time, space);
            return new StaticAnalysisResult(
                    kind,
                    ComplexityExprSimplifier.simplify(time),
                    timeBasis,
                    timeConfidence,
                    ComplexityExprSimplifier.simplify(space),
                    spaceConfidence,
                    variables,
                    findings,
                    limitations,
                    evidence,
                    null);
        } catch (Exception ex) {
            findings.add(new StaticFindingDraft("PARSER", null, null, null, "NONE",
                    "Parse failed: " + safeMessage(ex.getMessage())));
            return unsupported(findings, limitations, "PARSER_UNSUPPORTED", "Java source could not be parsed");
        }
    }

    private static StaticAnalysisResult unsupported(
            List<StaticFindingDraft> findings,
            List<String> limitations,
            String code,
            String message) {
        limitations.add(message);
        return new StaticAnalysisResult(
                ComplexityResultKind.UNSUPPORTED,
                new ComplexityExpr.Unknown(message),
                ComplexityBoundBasis.WORST_CASE,
                null,
                new ComplexityExpr.Unknown(message),
                null,
                Map.of(),
                findings,
                limitations,
                List.of(),
                code);
    }

    private static ComplexityResultKind classify(ComplexityExpr time, AnalysisState state) {
        if (time instanceof ComplexityExpr.Unknown) {
            return state.hasOpaqueDependency() ? ComplexityResultKind.INCONCLUSIVE : ComplexityResultKind.UNSUPPORTED;
        }
        if (state.hasOpaqueDependency() || state.hasUnknownLoop()) {
            return ComplexityResultKind.INCONCLUSIVE;
        }
        return ComplexityResultKind.STATIC_ONLY;
    }

    private static ComplexityConfidence confidenceFor(AnalysisState state, ComplexityExpr expr) {
        if (expr instanceof ComplexityExpr.Unknown) {
            return null;
        }
        if (state.hasOpaqueDependency() || state.hasUnknownLoop()) {
            return ComplexityConfidence.LOW;
        }
        if (state.hasAssumptionBasis()) {
            return ComplexityConfidence.MEDIUM;
        }
        return ComplexityConfidence.HIGH;
    }

    private static List<String> buildEvidence(AnalysisState state, ComplexityExpr time, ComplexityExpr space) {
        List<String> evidence = new ArrayList<>();
        evidence.add("Static time: " + ComplexityExprSimplifier.toBigOString(time));
        evidence.add("Static auxiliary space: " + ComplexityExprSimplifier.toBigOString(space));
        if (!state.limitations.isEmpty()) {
            evidence.add("Limitations: " + String.join(", ", state.limitations));
        }
        return evidence;
    }

    private static Map<String, String> mergeVariables(QuestionMetadataApiDto metadata, MethodDeclaration entryMethod) {
        Map<String, String> variables = new LinkedHashMap<>(ParameterVariableMapper.fromMetadata(metadata));
        if (variables.isEmpty()) {
            variables.putAll(ParameterVariableMapper.fromParameters(entryMethod.getParameters()));
        }
        return variables;
    }

    private static Optional<MethodDeclaration> resolveEntryMethod(CompilationUnit unit, QuestionMetadataApiDto metadata) {
        List<ClassOrInterfaceDeclaration> types = unit.findAll(ClassOrInterfaceDeclaration.class);
        if (types.isEmpty()) {
            return Optional.empty();
        }
        String targetName = metadata != null ? metadata.getFunctionName() : null;
        for (ClassOrInterfaceDeclaration type : types) {
            for (MethodDeclaration method : type.getMethods()) {
                if (method.isAbstract() || "main".equalsIgnoreCase(method.getNameAsString())) {
                    continue;
                }
                if (targetName != null && targetName.equals(method.getNameAsString())) {
                    return Optional.of(method);
                }
            }
        }
        for (ClassOrInterfaceDeclaration type : types) {
            Optional<MethodDeclaration> candidate = type.getMethods().stream()
                    .filter(m -> !m.isAbstract())
                    .filter(m -> !"main".equalsIgnoreCase(m.getNameAsString()))
                    .findFirst();
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static void detectMutableStatic(CompilationUnit unit, AnalysisState state) {
        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            if (!field.isStatic()) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                if (variable.getInitializer().isEmpty()) {
                    state.addFinding("MUTABLE_STATIC", field.getBegin().map(p -> p.line).orElse(null),
                            field.getEnd().map(p -> p.line).orElse(null), null, "HIGH",
                            "Mutable or uninitialized static field " + variable.getNameAsString());
                    state.limitations.add("MUTABLE_STATIC_STATE");
                }
            }
        }
    }

    private static Map<String, MethodDeclaration> indexMethods(CompilationUnit unit) {
        Map<String, MethodDeclaration> methods = new HashMap<>();
        for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
            if (!method.isAbstract()) {
                methods.putIfAbsent(method.getNameAsString(), method);
            }
        }
        return methods;
    }

    private ComplexityExpr analyzeBlock(
            BlockStmt block,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod,
            int depth) {
        List<ComplexityExpr> sequential = new ArrayList<>();
        for (Statement statement : block.getStatements()) {
            sequential.add(analyzeStatement(statement, state, methodIndex, memo, currentMethod, depth));
        }
        if (sequential.isEmpty()) {
            return ComplexityExpr.one();
        }
        return sequential.stream()
                .reduce((left, right) -> new ComplexityExpr.Sum(List.of(left, right)))
                .map(ComplexityExprSimplifier::simplify)
                .orElse(ComplexityExpr.one());
    }

    private ComplexityExpr analyzeStatement(
            Statement statement,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod,
            int depth) {
        return switch (statement) {
            case BlockStmt block -> analyzeBlock(block, state, methodIndex, memo, currentMethod, depth);
            case IfStmt ifStmt -> {
                ComplexityExpr thenCost = ifStmt.getThenStmt() instanceof BlockStmt b
                        ? analyzeBlock(b, state, methodIndex, memo, currentMethod, depth)
                        : analyzeStatement(ifStmt.getThenStmt(), state, methodIndex, memo, currentMethod, depth);
                ComplexityExpr elseCost = ifStmt.getElseStmt()
                        .map(s -> s instanceof BlockStmt b
                                ? analyzeBlock(b, state, methodIndex, memo, currentMethod, depth)
                                : analyzeStatement(s, state, methodIndex, memo, currentMethod, depth))
                        .orElse(ComplexityExpr.one());
                yield branchMax(thenCost, elseCost);
            }
            case ForStmt forStmt -> analyzeForLoop(forStmt, state, methodIndex, memo, currentMethod, depth);
            case ForEachStmt forEach -> analyzeForEach(forEach, state, methodIndex, memo, currentMethod, depth);
            case WhileStmt whileStmt -> analyzeWhile(whileStmt, state, methodIndex, memo, currentMethod, depth);
            case DoStmt doStmt -> {
                state.markUnknownLoop("do-while termination not proven");
                ComplexityExpr body = doStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlock(b, state, methodIndex, memo, currentMethod, depth + 1)
                        : analyzeStatement(doStmt.getBody(), state, methodIndex, memo, currentMethod, depth + 1);
                yield new ComplexityExpr.Product(List.of(new ComplexityExpr.Unknown("do-while"), body));
            }
            case ReturnStmt returnStmt -> returnStmt.getExpression()
                    .map(expr -> analyzeExpression(expr, state, methodIndex, memo, currentMethod))
                    .orElse(ComplexityExpr.one());
            case ExpressionStmt expressionStmt -> {
                if (expressionStmt.getExpression() == null) {
                    yield ComplexityExpr.one();
                }
                yield analyzeExpression(expressionStmt.getExpression(), state, methodIndex, memo, currentMethod);
            }
            default -> ComplexityExpr.one();
        };
    }

    private ComplexityExpr analyzeForLoop(
            ForStmt forStmt,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod,
            int depth) {
        Optional<ComplexityExpr> bound = extractLinearBound(forStmt.getCompare().orElse(null), state);
        ComplexityExpr body = forStmt.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, methodIndex, memo, currentMethod, depth + 1)
                : analyzeStatement(forStmt.getBody(), state, methodIndex, memo, currentMethod, depth + 1);
        if (bound.isEmpty()) {
            state.markUnknownLoop("non-linear or unmapped for-loop bound");
            return new ComplexityExpr.Product(List.of(new ComplexityExpr.Unknown("loop"), body));
        }
        state.addFinding("LOOP", forStmt.getBegin().map(p -> p.line).orElse(null),
                forStmt.getEnd().map(p -> p.line).orElse(null),
                ComplexityExprSimplifier.toExpressionString(bound.get()), "MEDIUM",
                "Linear loop with bound " + ComplexityExprSimplifier.toExpressionString(bound.get()));
        return new ComplexityExpr.Product(List.of(bound.get(), body));
    }

    private ComplexityExpr analyzeForEach(
            ForEachStmt forEach,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod,
            int depth) {
        ComplexityExpr iterableSize = mapIterableSize(forEach.getIterable(), state);
        ComplexityExpr body = forEach.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, methodIndex, memo, currentMethod, depth + 1)
                : analyzeStatement(forEach.getBody(), state, methodIndex, memo, currentMethod, depth + 1);
        if (iterableSize instanceof ComplexityExpr.Unknown) {
            state.markUnknownLoop("enhanced-for iterable size unknown");
        }
        return new ComplexityExpr.Product(List.of(iterableSize, body));
    }

    private ComplexityExpr analyzeWhile(
            WhileStmt whileStmt,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod,
            int depth) {
        Optional<ComplexityExpr> bound = extractLinearBound(whileStmt.getCondition(), state);
        ComplexityExpr body = whileStmt.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, methodIndex, memo, currentMethod, depth + 1)
                : analyzeStatement(whileStmt.getBody(), state, methodIndex, memo, currentMethod, depth + 1);
        if (bound.isEmpty()) {
            state.markUnknownLoop("while-loop termination not proven");
            return new ComplexityExpr.Product(List.of(new ComplexityExpr.Unknown("while"), body));
        }
        return new ComplexityExpr.Product(List.of(bound.get(), body));
    }

    private Optional<ComplexityExpr> extractLinearBound(Expression compare, AnalysisState state) {
        if (compare == null) {
            return Optional.empty();
        }
        if (compare instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.LESS) {
            return mapSizeExpression(binary.getRight(), state);
        }
        if (compare instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.LESS_EQUALS) {
            return mapSizeExpression(binary.getRight(), state);
        }
        return Optional.empty();
    }

    private ComplexityExpr mapIterableSize(Expression iterable, AnalysisState state) {
        if (iterable instanceof NameExpr name) {
            return state.variables.containsKey("n")
                    ? ComplexityExpr.var("n")
                    : ComplexityExpr.var(name.getNameAsString());
        }
        if (iterable instanceof FieldAccessExpr access && access.getNameAsString().equals("length")) {
            return mapSizeExpression(access.getScope(), state).orElse(new ComplexityExpr.Unknown("length scope"));
        }
        return new ComplexityExpr.Unknown("iterable size");
    }

    private Optional<ComplexityExpr> mapSizeExpression(Expression expression, AnalysisState state) {
        if (expression instanceof NameExpr name) {
            String mapped = state.mapNameToVariable(name.getNameAsString());
            return Optional.of(ComplexityExpr.var(mapped));
        }
        if (expression instanceof FieldAccessExpr access) {
            if ("length".equals(access.getNameAsString())) {
                return Optional.of(ComplexityExpr.var(state.primarySizeVariable()));
            }
        }
        if (expression instanceof MethodCallExpr call && "size".equals(call.getNameAsString())) {
            return Optional.of(ComplexityExpr.var(state.primarySizeVariable()));
        }
        if (expression instanceof IntegerLiteralExpr literal) {
            return Optional.of(new ComplexityExpr.Constant(Integer.parseInt(literal.getValue())));
        }
        return Optional.empty();
    }

    private ComplexityExpr analyzeExpression(
            Expression expression,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod) {
        if (expression instanceof NameExpr name) {
            return ComplexityExpr.var(state.mapNameToVariable(name.getNameAsString()));
        }
        if (expression instanceof BinaryExpr binary) {
            if (binary.getOperator() == BinaryExpr.Operator.PLUS) {
                return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                        analyzeExpression(binary.getLeft(), state, methodIndex, memo, currentMethod),
                        analyzeExpression(binary.getRight(), state, methodIndex, memo, currentMethod))));
            }
            if (binary.getOperator() == BinaryExpr.Operator.MULTIPLY) {
                return ComplexityExprSimplifier.simplify(new ComplexityExpr.Product(List.of(
                        analyzeExpression(binary.getLeft(), state, methodIndex, memo, currentMethod),
                        analyzeExpression(binary.getRight(), state, methodIndex, memo, currentMethod))));
            }
        }
        if (expression instanceof MethodCallExpr call) {
            return analyzeMethodCall(call, state, methodIndex, memo, currentMethod);
        }
        if (expression instanceof ObjectCreationExpr creation) {
            ComplexityExpr size = creation.getArguments().isEmpty()
                    ? ComplexityExpr.one()
                    : analyzeExpression(creation.getArguments().get(0), state, methodIndex, memo, currentMethod);
            state.addFinding("ALLOCATION", creation.getBegin().map(p -> p.line).orElse(null),
                    creation.getEnd().map(p -> p.line).orElse(null),
                    ComplexityExprSimplifier.toExpressionString(size), "MEDIUM",
                    "Object allocation " + creation.getType().getNameAsString());
            return size;
        }
        if (expression instanceof ArrayCreationExpr arrayCreation && !arrayCreation.getLevels().isEmpty()) {
            var level = arrayCreation.getLevels().get(0);
            if (level.getDimension().isPresent()) {
                return analyzeExpression(level.getDimension().get(), state, methodIndex, memo, currentMethod);
            }
        }
        return ComplexityExpr.one();
    }

    private ComplexityExpr analyzeMethodCall(
            MethodCallExpr call,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod) {
        String methodName = call.getNameAsString();
        if (call.getScope().isEmpty() && methodIndex.containsKey(methodName)) {
            if (methodName.equals(currentMethod)) {
                ComplexityExpr bodyCost = memo.getOrDefault(methodName, ComplexityExpr.one());
                ComplexityExpr recurrence = analyzeRecurrence(call, state, bodyCost);
                state.addFinding("RECURSION", call.getBegin().map(p -> p.line).orElse(null),
                        call.getEnd().map(p -> p.line).orElse(null),
                        ComplexityExprSimplifier.toExpressionString(recurrence), "MEDIUM",
                        "Direct recursion detected");
                return recurrence;
            }
            return memo.computeIfAbsent(methodName, name -> {
                MethodDeclaration method = methodIndex.get(name);
                return analyzeBlock(method.getBody().orElse(new BlockStmt()), state, methodIndex, memo, name, 0);
            });
        }
        String scopeType = call.getScope()
                .map(scope -> scope instanceof NameExpr n ? n.getNameAsString() : scope.toString())
                .orElse("java.lang");
        Optional<JdkKnowledgeEntry> jdk = knowledgeBase.match(scopeType, methodName);
        if (jdk.isPresent()) {
            JdkKnowledgeEntry entry = jdk.get();
            state.noteBasis(entry.boundBasis());
            if (entry.timeExpression() instanceof ComplexityExpr.Unknown) {
                state.markOpaque("JDK operation partially modeled: " + entry.pattern());
            }
            state.addFinding("JDK_CALL", call.getBegin().map(p -> p.line).orElse(null),
                    call.getEnd().map(p -> p.line).orElse(null),
                    ComplexityExprSimplifier.toExpressionString(entry.timeExpression()), "MEDIUM",
                    entry.note());
            return entry.timeExpression();
        }
        state.markOpaque("Unresolved external call: " + scopeType + "." + methodName);
        state.addFinding("OPAQUE_CALL", call.getBegin().map(p -> p.line).orElse(null),
                call.getEnd().map(p -> p.line).orElse(null), null, "LOW",
                "Unresolved call " + scopeType + "." + methodName + " — not treated as O(1)");
        return new ComplexityExpr.Unknown("opaque call");
    }

    private ComplexityExpr analyzeRecurrence(MethodCallExpr call, AnalysisState state, ComplexityExpr bodyCost) {
        if (call.getArguments().size() == 1) {
            Expression arg = call.getArgument(0);
            if (arg instanceof BinaryExpr binary
                    && binary.getOperator() == BinaryExpr.Operator.MINUS
                    && binary.getRight() instanceof IntegerLiteralExpr lit
                    && "1".equals(lit.getValue())) {
                return ComplexityExpr.var(state.primarySizeVariable());
            }
            if (arg instanceof BinaryExpr divide
                    && divide.getOperator() == BinaryExpr.Operator.DIVIDE
                    && divide.getRight() instanceof IntegerLiteralExpr divLit
                    && "2".equals(divLit.getValue())) {
                String v = state.primarySizeVariable();
                ComplexityExpr logFactor = new ComplexityExpr.Log(new ComplexityExpr.Variable(v));
                return new ComplexityExpr.Product(List.of(logFactor, bodyCost));
            }
        }
        return new ComplexityExpr.Unknown("unresolved recursion");
    }

    private ComplexityExpr analyzeAuxiliarySpace(
            MethodDeclaration entryMethod,
            CompilationUnit unit,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo) {
        List<ComplexityExpr> allocations = new ArrayList<>();
        entryMethod.walk(ArrayCreationExpr.class, array -> {
            if (!array.getLevels().isEmpty() && array.getLevels().get(0).getDimension().isPresent()) {
                allocations.add(analyzeExpression(
                        array.getLevels().get(0).getDimension().get(),
                        state, methodIndex, memo, entryMethod.getNameAsString()));
            }
        });
        entryMethod.walk(ObjectCreationExpr.class, creation -> {
            if (creation.getArguments().isEmpty()) {
                allocations.add(ComplexityExpr.one());
            } else {
                allocations.add(analyzeExpression(
                        creation.getArguments().get(0), state, methodIndex, memo, entryMethod.getNameAsString()));
            }
        });
        ComplexityExpr recursionStack = unit.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals(entryMethod.getNameAsString()))
                .findFirst()
                .map(call -> ComplexityExpr.var(state.primarySizeVariable()))
                .orElse(ComplexityExpr.one());

        List<ComplexityExpr> terms = new ArrayList<>(allocations);
        terms.add(recursionStack);
        if (terms.isEmpty()) {
            return ComplexityExpr.one();
        }
        return terms.stream()
                .reduce((a, b) -> new ComplexityExpr.Sum(List.of(a, b)))
                .map(ComplexityExprSimplifier::simplify)
                .orElse(ComplexityExpr.one());
    }

    private static ComplexityExpr branchMax(ComplexityExpr left, ComplexityExpr right) {
        if (left instanceof ComplexityExpr.Unknown) {
            return right;
        }
        if (right instanceof ComplexityExpr.Unknown) {
            return left;
        }
        return ComplexityExprSimplifier.rank(left) >= ComplexityExprSimplifier.rank(right) ? left : right;
    }

    private static String safeMessage(String message) {
        return message == null ? "parse error" : message;
    }

    static final class AnalysisState {
        final List<StaticFindingDraft> findings;
        final List<String> limitations;
        final Map<String, String> variables;
        private final JdkKnowledgeBase knowledgeBase;
        private boolean opaque;
        private boolean unknownLoop;
        private ComplexityBoundBasis worstBasis = ComplexityBoundBasis.WORST_CASE;

        AnalysisState(List<StaticFindingDraft> findings, List<String> limitations, Map<String, String> variables, JdkKnowledgeBase knowledgeBase) {
            this.findings = findings;
            this.limitations = limitations;
            this.variables = variables;
            this.knowledgeBase = knowledgeBase;
        }

        void addFinding(String category, Integer start, Integer end, String expression, String certainty, String summary) {
            if (findings.size() >= 50) {
                return;
            }
            findings.add(new StaticFindingDraft(category, start, end, expression, certainty, summary));
        }

        void markOpaque(String reason) {
            opaque = true;
            limitations.add(reason);
        }

        void markUnknownLoop(String reason) {
            unknownLoop = true;
            limitations.add(reason);
        }

        void noteBasis(ComplexityBoundBasis basis) {
            if (basis == ComplexityBoundBasis.EXPECTED_ASSUMPTION || basis == ComplexityBoundBasis.AMORTIZED_ASSUMPTION) {
                worstBasis = basis;
            }
        }

        boolean hasOpaqueDependency() {
            return opaque;
        }

        boolean hasUnknownLoop() {
            return unknownLoop;
        }

        boolean hasAssumptionBasis() {
            return worstBasis != ComplexityBoundBasis.WORST_CASE;
        }

        ComplexityBoundBasis worstTimeBasis() {
            return worstBasis;
        }

        String primarySizeVariable() {
            return variables.containsKey("n") ? "n" : variables.keySet().stream().findFirst().orElse("n");
        }

        String mapNameToVariable(String name) {
            if (variables.containsKey(name)) {
                return name;
            }
            return primarySizeVariable();
        }
    }
}
