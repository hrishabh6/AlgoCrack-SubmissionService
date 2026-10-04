package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.Parameter;
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
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkCallTarget;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkCallTargetResolver;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeEntry;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.recurrence.RecurrenceSupport;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.recurrence.RecurrenceSupport.ArgumentPattern;
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

    public static final String ANALYZER_VERSION = "static-v1.2";
    public static final String CONFIDENCE_MODEL_VERSION = "static-confidence-v1";

    private final JdkKnowledgeBase knowledgeBase;

    public StaticAnalysisResult analyze(String source, QuestionMetadataApiDto metadata) {
        return analyzeInternal(source, metadata, true);
    }

    /**
     * Test/snippet helper — allows metadata-free entry resolution fallback. Not used in production pipeline.
     */
    public StaticAnalysisResult analyzeSnippet(String source, QuestionMetadataApiDto metadata) {
        return analyzeInternal(source, metadata, false);
    }

    private StaticAnalysisResult analyzeInternal(String source, QuestionMetadataApiDto metadata, boolean requireMetadataEntry) {
        List<StaticFindingDraft> findings = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        try {
            CompilationUnit unit = StaticJavaParser.parse(source);
            Optional<MethodDeclaration> entry = resolveEntryMethod(unit, metadata, requireMetadataEntry);
            if (entry.isEmpty()) {
                return unsupported(findings, limitations, "ENTRY_METHOD_UNRESOLVED",
                        "Could not resolve entry method for complexity analysis");
            }
            MethodDeclaration entryMethod = entry.get();
            Map<String, String> variables = mergeVariables(metadata, entryMethod);
            AnalysisState state = new AnalysisState(findings, limitations, variables, knowledgeBase, unit, entryMethod, metadata);

            detectMutableStatic(unit, state);

            Map<String, MethodDeclaration> methodIndex = indexMethods(unit);
            Map<String, ComplexityExpr> memo = new HashMap<>();
            ComplexityExpr time = analyzeBlock(entryMethod.getBody().orElse(new BlockStmt()), state, methodIndex, memo, entryMethod.getNameAsString(), 0);
            ComplexityExpr space = analyzeAuxiliarySpacePeak(entryMethod, state, methodIndex, memo);

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

    private static Optional<MethodDeclaration> resolveEntryMethod(
            CompilationUnit unit,
            QuestionMetadataApiDto metadata,
            boolean requireMetadataEntry) {
        List<ClassOrInterfaceDeclaration> types = unit.findAll(ClassOrInterfaceDeclaration.class);
        if (types.isEmpty()) {
            return Optional.empty();
        }
        String targetName = metadata != null ? metadata.getFunctionName() : null;
        if (requireMetadataEntry && (targetName == null || targetName.isBlank())) {
            return Optional.empty();
        }
        if (targetName != null && !targetName.isBlank()) {
            for (ClassOrInterfaceDeclaration type : types) {
                for (MethodDeclaration method : type.getMethods()) {
                    if (method.isAbstract() || "main".equalsIgnoreCase(method.getNameAsString())) {
                        continue;
                    }
                    if (targetName.equals(method.getNameAsString())) {
                        return Optional.of(method);
                    }
                }
            }
            return Optional.empty();
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
                yield ComplexityExprSimplifier.branchWorstCase(thenCost, elseCost);
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
            return ComplexityExpr.var(state.mapNameToVariable(name.getNameAsString()));
        }
        if (iterable instanceof FieldAccessExpr access && "length".equals(access.getNameAsString())) {
            if (access.getScope() instanceof NameExpr nameExpr) {
                return ComplexityExpr.var(state.mapNameToVariable(nameExpr.getNameAsString()));
            }
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
                String scopeName = access.getScope() instanceof NameExpr nameExpr
                        ? nameExpr.getNameAsString()
                        : access.getScope().toString();
                return Optional.of(ComplexityExpr.var(state.mapNameToVariable(scopeName)));
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
                String recurrenceKey = currentMethod + "#recurrence";
                if (!memo.containsKey(recurrenceKey)) {
                    memo.put(recurrenceKey, new ComplexityExpr.Unknown("recurrence pending"));
                    MethodDeclaration method = methodIndex.get(currentMethod);
                    ComplexityExpr resolved = RecurrenceSupport.resolveMethodRecurrence(
                            method,
                            currentMethod,
                            state.primarySizeVariable(),
                            expr -> analyzeExpression(expr, state, methodIndex, memo, currentMethod));
                    if (resolved instanceof ComplexityExpr.Unknown) {
                        state.markOpaque("Recurrence pattern not supported");
                    }
                    memo.put(recurrenceKey, resolved);
                }
                ComplexityExpr recurrence = memo.get(recurrenceKey);
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
        Optional<JdkCallTarget> staticTarget = JdkCallTargetResolver.resolve(call, state.compilationUnit);
        int argCount = call.getArguments().size();
        Optional<JdkKnowledgeEntry> jdk = staticTarget.flatMap(t -> knowledgeBase.matchQualified(t, methodName, argCount));
        if (jdk.isEmpty()) {
            jdk = JdkCallTargetResolver.resolveInstanceCall(call, state.paramTypes, state.compilationUnit)
                    .flatMap(t -> knowledgeBase.matchQualified(t, methodName, argCount));
        }
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
        String scopeLabel = call.getScope().map(Object::toString).orElse("unknown");
        state.markOpaque("Unresolved external call: " + scopeLabel + "." + methodName);
        state.addFinding("OPAQUE_CALL", call.getBegin().map(p -> p.line).orElse(null),
                call.getEnd().map(p -> p.line).orElse(null), null, "LOW",
                "Unresolved call " + scopeLabel + "." + methodName + " — not treated as O(1)");
        return new ComplexityExpr.Unknown("opaque call");
    }

    private ComplexityExpr analyzeAuxiliarySpacePeak(
            MethodDeclaration entryMethod,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo) {
        ComplexityExpr allocationPeak = analyzeBlockSpacePeak(
                entryMethod.getBody().orElse(new BlockStmt()), state, methodIndex, memo, entryMethod.getNameAsString(), false);

        ArgumentPattern stackPattern = RecurrenceSupport.unifiedStackPattern(
                entryMethod, entryMethod.getNameAsString());

        ComplexityExpr stack = RecurrenceSupport.recursionStackDepth(stackPattern, state.primarySizeVariable());
        if (stackPattern == ArgumentPattern.UNSUPPORTED
                && !RecurrenceSupport.directSelfCalls(entryMethod, entryMethod.getNameAsString()).isEmpty()) {
            stack = new ComplexityExpr.Unknown("recursion stack depth unknown");
        }

        return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(allocationPeak, stack)));
    }

    private ComplexityExpr analyzeBlockSpacePeak(
            BlockStmt block,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod,
            boolean disjointNestedScope) {
        ComplexityExpr scopeLive = ComplexityExpr.one();
        ComplexityExpr nestedDisjointPeak = ComplexityExpr.one();
        for (Statement statement : block.getStatements()) {
            if (statement instanceof ExpressionStmt exprStmt && exprStmt.getExpression() instanceof VariableDeclarationExpr varDecl) {
                for (var variable : varDecl.getVariables()) {
                    if (variable.getInitializer().isPresent()) {
                        ComplexityExpr alloc = allocationSize(
                                variable.getInitializer().get(), state, methodIndex, memo, currentMethod);
                        scopeLive = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(scopeLive, alloc)));
                    }
                }
                continue;
            }
            ComplexityExpr stmtPeak = analyzeStatementSpacePeak(
                    statement, state, methodIndex, memo, currentMethod, disjointNestedScope);
            nestedDisjointPeak = ComplexityExprSimplifier.branchWorstCase(nestedDisjointPeak, stmtPeak);
        }
        if (disjointNestedScope) {
            return ComplexityExprSimplifier.branchWorstCase(scopeLive, nestedDisjointPeak);
        }
        return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(scopeLive, nestedDisjointPeak)));
    }

    private ComplexityExpr analyzeStatementSpacePeak(
            Statement statement,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod,
            boolean parentScope) {
        return switch (statement) {
            case BlockStmt block -> analyzeBlockSpacePeak(block, state, methodIndex, memo, currentMethod, true);
            case ForStmt forStmt -> {
                ComplexityExpr body = forStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, methodIndex, memo, currentMethod, true)
                        : analyzeStatementSpacePeak(forStmt.getBody(), state, methodIndex, memo, currentMethod, parentScope);
                yield body;
            }
            case ForEachStmt forEach -> {
                ComplexityExpr body = forEach.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, methodIndex, memo, currentMethod, true)
                        : analyzeStatementSpacePeak(forEach.getBody(), state, methodIndex, memo, currentMethod, parentScope);
                yield body;
            }
            case WhileStmt whileStmt -> {
                ComplexityExpr body = whileStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, methodIndex, memo, currentMethod, true)
                        : analyzeStatementSpacePeak(whileStmt.getBody(), state, methodIndex, memo, currentMethod, parentScope);
                yield body;
            }
            case ReturnStmt returnStmt -> returnStmt.getExpression()
                    .map(expr -> allocationSize(expr, state, methodIndex, memo, currentMethod))
                    .orElse(ComplexityExpr.one());
            case ExpressionStmt expressionStmt -> expressionStmt.getExpression() == null
                    ? ComplexityExpr.one()
                    : allocationSize(expressionStmt.getExpression(), state, methodIndex, memo, currentMethod);
            case IfStmt ifStmt -> {
                ComplexityExpr thenPeak = ifStmt.getThenStmt() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, methodIndex, memo, currentMethod, true)
                        : analyzeStatementSpacePeak(ifStmt.getThenStmt(), state, methodIndex, memo, currentMethod, parentScope);
                ComplexityExpr elsePeak = ifStmt.getElseStmt()
                        .map(s -> s instanceof BlockStmt b
                                ? analyzeBlockSpacePeak(b, state, methodIndex, memo, currentMethod, true)
                                : analyzeStatementSpacePeak(s, state, methodIndex, memo, currentMethod, parentScope))
                        .orElse(ComplexityExpr.one());
                yield ComplexityExprSimplifier.branchWorstCase(thenPeak, elsePeak);
            }
            default -> ComplexityExpr.one();
        };
    }

    private ComplexityExpr allocationSize(
            Expression expression,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            String currentMethod) {
        if (expression instanceof ArrayCreationExpr array && !array.getLevels().isEmpty()
                && array.getLevels().get(0).getDimension().isPresent()) {
            return analyzeExpression(
                    array.getLevels().get(0).getDimension().get(), state, methodIndex, memo, currentMethod);
        }
        if (expression instanceof ObjectCreationExpr creation) {
            if (creation.getArguments().isEmpty()) {
                return ComplexityExpr.one();
            }
            return analyzeExpression(creation.getArguments().get(0), state, methodIndex, memo, currentMethod);
        }
        return ComplexityExpr.one();
    }

    private static Map<String, String> buildDeclaredTypes(CompilationUnit unit, MethodDeclaration entryMethod) {
        Map<String, String> types = new HashMap<>();
        for (Parameter param : entryMethod.getParameters()) {
            types.put(param.getNameAsString(), resolveTypeName(unit, param.getType().asString()));
        }
        return types;
    }

    private static String resolveTypeName(CompilationUnit unit, String typeName) {
        String base = typeName.trim();
        if (base.contains("<")) {
            base = base.substring(0, base.indexOf('<'));
        }
        if (base.contains(".")) {
            return base;
        }
        for (ImportDeclaration importDeclaration : unit.getImports()) {
            if (importDeclaration.isStatic()) {
                continue;
            }
            String imported = importDeclaration.getNameAsString();
            if (imported.endsWith("." + base)) {
                return imported;
            }
        }
        return base;
    }

    private static String safeMessage(String message) {
        return message == null ? "parse error" : message;
    }

    static final class AnalysisState {
        final List<StaticFindingDraft> findings;
        final List<String> limitations;
        final Map<String, String> variables;
        final CompilationUnit compilationUnit;
        final Map<String, String> paramTypes;
        final Map<String, String> paramNameToSymbol;
        private boolean opaque;
        private boolean unknownLoop;
        private ComplexityBoundBasis worstBasis = ComplexityBoundBasis.WORST_CASE;

        AnalysisState(
                List<StaticFindingDraft> findings,
                List<String> limitations,
                Map<String, String> variables,
                JdkKnowledgeBase knowledgeBase,
                CompilationUnit compilationUnit,
                MethodDeclaration entryMethod,
                QuestionMetadataApiDto metadata) {
            this.findings = findings;
            this.limitations = limitations;
            this.variables = variables;
            this.compilationUnit = compilationUnit;
            this.paramTypes = buildDeclaredTypes(compilationUnit, entryMethod);
            this.paramNameToSymbol = buildParamNameToSymbol(metadata, entryMethod);
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
            if (paramNameToSymbol.containsKey(name)) {
                return paramNameToSymbol.get(name);
            }
            if (variables.containsKey(name)) {
                return name;
            }
            return primarySizeVariable();
        }
    }

    private static Map<String, String> buildParamNameToSymbol(QuestionMetadataApiDto metadata, MethodDeclaration entryMethod) {
        Map<String, String> mapping = new HashMap<>();
        if (metadata != null && metadata.getParamNames() != null && metadata.getParamTypes() != null) {
            List<String> names = metadata.getParamNames();
            List<String> types = metadata.getParamTypes();
            for (int i = 0; i < names.size() && i < types.size(); i++) {
                mapping.put(names.get(i), ParameterVariableMapper.symbolForParameter(types.get(i), names.get(i), i));
            }
            return mapping;
        }
        List<Parameter> parameters = entryMethod.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            mapping.put(parameter.getNameAsString(),
                    ParameterVariableMapper.symbolForParameter(parameter.getType().asString(), parameter.getNameAsString(), i));
        }
        return mapping;
    }
}
