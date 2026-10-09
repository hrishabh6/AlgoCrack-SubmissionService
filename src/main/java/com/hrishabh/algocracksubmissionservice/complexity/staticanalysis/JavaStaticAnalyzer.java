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
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.loop.ConservativeLoopBoundProof;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.AnalysisDimensionCompleteness;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;
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

    public static final String ANALYZER_VERSION = "static-v2.0-dev";
    public static final String CONFIDENCE_MODEL_VERSION = "static-confidence-v2-dev";

    private static final int MAX_HELPER_ANALYSIS_DEPTH = 128;

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
            Map<String, MethodVisitState> visitStates = new HashMap<>();
            ComplexityExpr time = analyzeBlock(entryMethod.getBody().orElse(new BlockStmt()), state, methodIndex, memo, visitStates, entryMethod.getNameAsString(), 0);
            ComplexityExpr space = analyzeAuxiliarySpacePeak(entryMethod, state, methodIndex, memo, visitStates);

            return buildFinalResult(state, variables, findings, limitations, time, space);
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
                code,
                List.of(StaticAnalysisReasonCode.INCOMPLETE_TIME_ANALYSIS),
                AnalysisDimensionCompleteness.INCOMPLETE,
                AnalysisDimensionCompleteness.INCOMPLETE,
                null);
    }

    private StaticAnalysisResult buildFinalResult(
            AnalysisState state,
            Map<String, String> variables,
            List<StaticFindingDraft> findings,
            List<String> limitations,
            ComplexityExpr rawTime,
            ComplexityExpr rawSpace) {
        ComplexityExpr diagnosticTime = ComplexityExprSimplifier.simplify(rawTime);
        ComplexityExpr diagnosticSpace = ComplexityExprSimplifier.simplify(rawSpace);
        ComplexityExpr authoritativeTime = diagnosticTime;
        ComplexityExpr authoritativeSpace = diagnosticSpace;
        ComplexityExpr diagnosticTimeOnly = null;

        if (state.timeCompleteness == AnalysisDimensionCompleteness.INCOMPLETE
                || !state.reasonCodes.isEmpty()
                || rawTime instanceof ComplexityExpr.Unknown) {
            if (!(authoritativeTime instanceof ComplexityExpr.Unknown)) {
                diagnosticTimeOnly = authoritativeTime;
            }
            authoritativeTime = new ComplexityExpr.Unknown("INCOMPLETE_TIME_ANALYSIS");
        }
        if (state.spaceCompleteness == AnalysisDimensionCompleteness.INCOMPLETE
                || rawSpace instanceof ComplexityExpr.Unknown) {
            authoritativeSpace = new ComplexityExpr.Unknown("INCOMPLETE_SPACE_ANALYSIS");
        }

        ComplexityBoundBasis timeBasis = state.worstTimeBasis();
        ComplexityResultKind kind = classify(authoritativeTime, state);
        ComplexityConfidence timeConfidence = confidenceFor(state, authoritativeTime);
        ComplexityConfidence spaceConfidence = authoritativeSpace instanceof ComplexityExpr.Unknown
                ? null
                : confidenceForSpace(state, authoritativeSpace);

        if (kind == ComplexityResultKind.STATIC_ONLY) {
            limitations.add("PROFILE_UNAVAILABLE");
        }

        List<String> evidence = buildEvidence(state, authoritativeTime, authoritativeSpace);
        return new StaticAnalysisResult(
                kind,
                authoritativeTime,
                timeBasis,
                timeConfidence,
                authoritativeSpace,
                spaceConfidence,
                variables,
                findings,
                limitations,
                evidence,
                null,
                List.copyOf(state.reasonCodes),
                state.timeCompleteness,
                state.spaceCompleteness,
                diagnosticTimeOnly);
    }

    private static ComplexityResultKind classify(ComplexityExpr time, AnalysisState state) {
        if (state.timeCompleteness == AnalysisDimensionCompleteness.INCOMPLETE || !state.reasonCodes.isEmpty()) {
            return state.hasOpaqueDependency() ? ComplexityResultKind.INCONCLUSIVE : ComplexityResultKind.UNSUPPORTED;
        }
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
        if (state.timeCompleteness == AnalysisDimensionCompleteness.INCOMPLETE || !state.reasonCodes.isEmpty()) {
            return ComplexityConfidence.LOW;
        }
        if (state.hasOpaqueDependency() || state.hasUnknownLoop()) {
            return ComplexityConfidence.LOW;
        }
        if (state.hasAssumptionBasis()) {
            return ComplexityConfidence.MEDIUM;
        }
        return ComplexityConfidence.HIGH;
    }

    private static ComplexityConfidence confidenceForSpace(AnalysisState state, ComplexityExpr expr) {
        if (expr instanceof ComplexityExpr.Unknown) {
            return null;
        }
        if (state.spaceCompleteness == AnalysisDimensionCompleteness.INCOMPLETE) {
            return ComplexityConfidence.LOW;
        }
        return confidenceFor(state, expr);
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
            Map<String, MethodVisitState> visitStates,
            String currentMethod,
            int depth) {
        List<ComplexityExpr> sequential = new ArrayList<>();
        for (Statement statement : block.getStatements()) {
            sequential.add(analyzeStatement(statement, state, methodIndex, memo, visitStates, currentMethod, depth));
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
            Map<String, MethodVisitState> visitStates,
            String currentMethod,
            int depth) {
        return switch (statement) {
            case BlockStmt block -> analyzeBlock(block, state, methodIndex, memo, visitStates, currentMethod, depth);
            case IfStmt ifStmt -> {
                ComplexityExpr conditionCost = analyzeExpression(
                        ifStmt.getCondition(), state, methodIndex, memo, visitStates, currentMethod);
                ComplexityExpr thenCost = ifStmt.getThenStmt() instanceof BlockStmt b
                        ? analyzeBlock(b, state, methodIndex, memo, visitStates, currentMethod, depth)
                        : analyzeStatement(ifStmt.getThenStmt(), state, methodIndex, memo, visitStates, currentMethod, depth);
                ComplexityExpr elseCost = ifStmt.getElseStmt()
                        .map(s -> s instanceof BlockStmt b
                                ? analyzeBlock(b, state, methodIndex, memo, visitStates, currentMethod, depth)
                                : analyzeStatement(s, state, methodIndex, memo, visitStates, currentMethod, depth))
                        .orElse(ComplexityExpr.one());
                ComplexityExpr branch = ComplexityExprSimplifier.branchWorstCase(thenCost, elseCost);
                yield ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(conditionCost, branch)));
            }
            case ForStmt forStmt -> analyzeForLoop(forStmt, state, methodIndex, memo, visitStates, currentMethod, depth);
            case ForEachStmt forEach -> analyzeForEach(forEach, state, methodIndex, memo, visitStates, currentMethod, depth);
            case WhileStmt whileStmt -> analyzeWhile(whileStmt, state, methodIndex, memo, visitStates, currentMethod, depth);
            case DoStmt doStmt -> {
                state.markUnknownLoop(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN);
                ComplexityExpr body = doStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlock(b, state, methodIndex, memo, visitStates, currentMethod, depth + 1)
                        : analyzeStatement(doStmt.getBody(), state, methodIndex, memo, visitStates, currentMethod, depth + 1);
                yield new ComplexityExpr.Unknown("do-while");
            }
            case SwitchStmt switchStmt -> {
                ComplexityExpr selector = analyzeExpression(
                        switchStmt.getSelector(), state, methodIndex, memo, visitStates, currentMethod);
                ComplexityExpr armWorst = ComplexityExpr.one();
                for (SwitchEntry entry : switchStmt.getEntries()) {
                    ComplexityExpr entryCost = ComplexityExpr.one();
                    for (Statement inner : entry.getStatements()) {
                        entryCost = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                                entryCost,
                                analyzeStatement(inner, state, methodIndex, memo, visitStates, currentMethod, depth))));
                    }
                    armWorst = ComplexityExprSimplifier.branchWorstCase(armWorst, entryCost);
                }
                yield ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(selector, armWorst)));
            }
            case TryStmt tryStmt -> {
                List<ComplexityExpr> parts = new ArrayList<>();
                parts.add(analyzeBlock(tryStmt.getTryBlock(), state, methodIndex, memo, visitStates, currentMethod, depth));
                for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                    parts.add(analyzeBlock(catchClause.getBody(), state, methodIndex, memo, visitStates, currentMethod, depth));
                }
                tryStmt.getFinallyBlock().ifPresent(finallyBlock ->
                        parts.add(analyzeBlock(finallyBlock, state, methodIndex, memo, visitStates, currentMethod, depth)));
                ComplexityExpr tryWorst = ComplexityExpr.one();
                for (ComplexityExpr part : parts) {
                    tryWorst = ComplexityExprSimplifier.branchWorstCase(tryWorst, part);
                }
                yield ComplexityExprSimplifier.simplify(tryWorst);
            }
            case ReturnStmt returnStmt -> returnStmt.getExpression()
                    .map(expr -> analyzeExpression(expr, state, methodIndex, memo, visitStates, currentMethod))
                    .orElse(ComplexityExpr.one());
            case ExpressionStmt expressionStmt -> {
                if (expressionStmt.getExpression() == null) {
                    yield ComplexityExpr.one();
                }
                yield analyzeExpression(expressionStmt.getExpression(), state, methodIndex, memo, visitStates, currentMethod);
            }
            default -> unsupportedStatement(statement, state);
        };
    }

    private static ComplexityExpr unsupportedStatement(Statement statement, AnalysisState state) {
        state.markTimeIncomplete(StaticAnalysisReasonCode.UNSUPPORTED_STATEMENT);
        return new ComplexityExpr.Unknown("unsupported statement: " + statement.getClass().getSimpleName());
    }

    private ComplexityExpr analyzeForLoop(
            ForStmt forStmt,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            Map<String, MethodVisitState> visitStates,
            String currentMethod,
            int depth) {
        ConservativeLoopBoundProof.BoundMapper mapper = boundMapper(state);
        ConservativeLoopBoundProof.ProofResult proof = ConservativeLoopBoundProof.proveForLoop(forStmt, mapper);
        ComplexityExpr body = forStmt.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, methodIndex, memo, visitStates, currentMethod, depth + 1)
                : analyzeStatement(forStmt.getBody(), state, methodIndex, memo, visitStates, currentMethod, depth + 1);
        if (proof.failure().isPresent()) {
            state.markUnknownLoop(proof.failure().get());
            return new ComplexityExpr.Unknown("loop");
        }
        ComplexityExpr bound = proof.iterationBound().orElseThrow();
        state.addFinding("LOOP", forStmt.getBegin().map(p -> p.line).orElse(null),
                forStmt.getEnd().map(p -> p.line).orElse(null),
                ComplexityExprSimplifier.toExpressionString(bound), "MEDIUM",
                "Counted loop with bound " + ComplexityExprSimplifier.toExpressionString(bound));
        return new ComplexityExpr.Product(List.of(bound, body));
    }

    private ComplexityExpr analyzeForEach(
            ForEachStmt forEach,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            Map<String, MethodVisitState> visitStates,
            String currentMethod,
            int depth) {
        ComplexityExpr iterableSize = resolveIterableSize(forEach.getIterable(), state);
        ComplexityExpr body = forEach.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, methodIndex, memo, visitStates, currentMethod, depth + 1)
                : analyzeStatement(forEach.getBody(), state, methodIndex, memo, visitStates, currentMethod, depth + 1);
        if (iterableSize instanceof ComplexityExpr.Unknown) {
            state.markUnknownLoop(StaticAnalysisReasonCode.UNKNOWN_LOOP_BOUND);
        }
        return new ComplexityExpr.Product(List.of(iterableSize, body));
    }

    private ComplexityExpr analyzeWhile(
            WhileStmt whileStmt,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            Map<String, MethodVisitState> visitStates,
            String currentMethod,
            int depth) {
        ConservativeLoopBoundProof.BoundMapper mapper = boundMapper(state);
        ConservativeLoopBoundProof.ProofResult proof = ConservativeLoopBoundProof.proveWhileLoop(whileStmt, mapper);
        ComplexityExpr body = whileStmt.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, methodIndex, memo, visitStates, currentMethod, depth + 1)
                : analyzeStatement(whileStmt.getBody(), state, methodIndex, memo, visitStates, currentMethod, depth + 1);
        if (proof.failure().isPresent()) {
            state.markUnknownLoop(proof.failure().get());
            return new ComplexityExpr.Unknown("loop");
        }
        ComplexityExpr bound = proof.iterationBound().orElseThrow();
        return new ComplexityExpr.Product(List.of(bound, body));
    }

    private ConservativeLoopBoundProof.BoundMapper boundMapper(AnalysisState state) {
        return new ConservativeLoopBoundProof.BoundMapper() {
            @Override
            public Optional<ComplexityExpr> mapBoundExpression(Expression expression) {
                return mapSizeExpression(expression, state);
            }

            @Override
            public Optional<ComplexityExpr> mapIterableSize(Expression iterable) {
                ComplexityExpr size = resolveIterableSize(iterable, state);
                if (size instanceof ComplexityExpr.Unknown) {
                    return Optional.empty();
                }
                return Optional.of(size);
            }
        };
    }

    /**
     * Cost of reading a variable by name. Input-size symbols are bound only in explicit size positions
     * ({@link #mapSizeExpression}), not for ordinary value reads.
     */
    private static ComplexityExpr evaluationCostOfName(String name, AnalysisState state) {
        return ComplexityExpr.one();
    }

    private ComplexityExpr sizeOrEvaluationCost(
            Expression expression,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            Map<String, MethodVisitState> visitStates,
            String currentMethod) {
        if (expression != null) {
            Optional<ComplexityExpr> sized = mapSizeExpression(expression, state);
            if (sized.isPresent()) {
                return sized.get();
            }
        }
        return analyzeExpression(expression, state, methodIndex, memo, visitStates, currentMethod);
    }

    private ComplexityExpr resolveIterableSize(Expression iterable, AnalysisState state) {
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
        if (expression instanceof MethodCallExpr call && "size".equals(call.getNameAsString()) && call.getScope().isPresent()) {
            Expression scope = call.getScope().get();
            if (scope instanceof NameExpr name && state.paramNameToSymbol.containsKey(name.getNameAsString())) {
                return Optional.of(ComplexityExpr.var(state.paramNameToSymbol.get(name.getNameAsString())));
            }
            return Optional.empty();
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
            Map<String, MethodVisitState> visitStates,
            String currentMethod) {
        if (expression == null) {
            return ComplexityExpr.one();
        }
        if (expression instanceof LiteralExpr) {
            return ComplexityExpr.one();
        }
        if (expression instanceof NameExpr name) {
            return evaluationCostOfName(name.getNameAsString(), state);
        }
        if (expression instanceof EnclosedExpr enclosed) {
            return analyzeExpression(enclosed.getInner(), state, methodIndex, memo, visitStates, currentMethod);
        }
        if (expression instanceof UnaryExpr unary) {
            return analyzeExpression(unary.getExpression(), state, methodIndex, memo, visitStates, currentMethod);
        }
        if (expression instanceof BinaryExpr binary) {
            if (binary.getOperator() == BinaryExpr.Operator.PLUS) {
                return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                        analyzeExpression(binary.getLeft(), state, methodIndex, memo, visitStates, currentMethod),
                        analyzeExpression(binary.getRight(), state, methodIndex, memo, visitStates, currentMethod))));
            }
            if (binary.getOperator() == BinaryExpr.Operator.MULTIPLY) {
                return ComplexityExprSimplifier.simplify(new ComplexityExpr.Product(List.of(
                        analyzeExpression(binary.getLeft(), state, methodIndex, memo, visitStates, currentMethod),
                        analyzeExpression(binary.getRight(), state, methodIndex, memo, visitStates, currentMethod))));
            }
            if (isPureEvaluationBinary(binary.getOperator())) {
                return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                        analyzeExpression(binary.getLeft(), state, methodIndex, memo, visitStates, currentMethod),
                        analyzeExpression(binary.getRight(), state, methodIndex, memo, visitStates, currentMethod))));
            }
            return unsupportedExpression(expression, state);
        }
        if (expression instanceof ConditionalExpr conditional) {
            ComplexityExpr condition = analyzeExpression(
                    conditional.getCondition(), state, methodIndex, memo, visitStates, currentMethod);
            ComplexityExpr thenExpr = analyzeExpression(
                    conditional.getThenExpr(), state, methodIndex, memo, visitStates, currentMethod);
            ComplexityExpr elseExpr = analyzeExpression(
                    conditional.getElseExpr(), state, methodIndex, memo, visitStates, currentMethod);
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                    condition, ComplexityExprSimplifier.branchWorstCase(thenExpr, elseExpr))));
        }
        if (expression instanceof VariableDeclarationExpr varDecl) {
            ComplexityExpr total = ComplexityExpr.one();
            for (VariableDeclarator variable : varDecl.getVariables()) {
                if (variable.getInitializer().isPresent()) {
                    total = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                            total,
                            analyzeExpression(variable.getInitializer().get(), state, methodIndex, memo, visitStates, currentMethod))));
                }
            }
            return total;
        }
        if (expression instanceof AssignExpr assign) {
            ComplexityExpr target = assign.getTarget() instanceof Expression targetExpr
                    ? analyzeExpression(targetExpr, state, methodIndex, memo, visitStates, currentMethod)
                    : ComplexityExpr.one();
            ComplexityExpr value = analyzeExpression(
                    assign.getValue(), state, methodIndex, memo, visitStates, currentMethod);
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(target, value)));
        }
        if (expression instanceof MethodCallExpr call) {
            return analyzeMethodCall(call, state, methodIndex, memo, visitStates, currentMethod);
        }
        if (expression instanceof ObjectCreationExpr creation) {
            ComplexityExpr size = creation.getArguments().isEmpty()
                    ? ComplexityExpr.one()
                    : analyzeExpression(creation.getArguments().get(0), state, methodIndex, memo, visitStates, currentMethod);
            state.addFinding("ALLOCATION", creation.getBegin().map(p -> p.line).orElse(null),
                    creation.getEnd().map(p -> p.line).orElse(null),
                    ComplexityExprSimplifier.toExpressionString(size), "MEDIUM",
                    "Object allocation " + creation.getType().getNameAsString());
            return size;
        }
        if (expression instanceof ArrayCreationExpr arrayCreation && !arrayCreation.getLevels().isEmpty()) {
            var level = arrayCreation.getLevels().get(0);
            if (level.getDimension().isPresent()) {
                return sizeOrEvaluationCost(
                        level.getDimension().get(), state, methodIndex, memo, visitStates, currentMethod);
            }
        }
        if (expression instanceof CastExpr cast) {
            return analyzeExpression(cast.getExpression(), state, methodIndex, memo, visitStates, currentMethod);
        }
        if (expression instanceof ArrayAccessExpr access) {
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                    analyzeExpression(access.getName(), state, methodIndex, memo, visitStates, currentMethod),
                    analyzeExpression(access.getIndex(), state, methodIndex, memo, visitStates, currentMethod))));
        }
        if (expression instanceof FieldAccessExpr access) {
            if ("length".equals(access.getNameAsString())) {
                return analyzeExpression(access.getScope(), state, methodIndex, memo, visitStates, currentMethod);
            }
            return analyzeExpression(access.getScope(), state, methodIndex, memo, visitStates, currentMethod);
        }
        return unsupportedExpression(expression, state);
    }

    private static boolean isPureEvaluationBinary(BinaryExpr.Operator operator) {
        return switch (operator) {
            case LESS, LESS_EQUALS, GREATER, GREATER_EQUALS, EQUALS, NOT_EQUALS,
                    AND, OR, MINUS, DIVIDE, REMAINDER -> true;
            default -> false;
        };
    }

    private static ComplexityExpr unsupportedExpression(Expression expression, AnalysisState state) {
        state.markTimeIncomplete(StaticAnalysisReasonCode.UNSUPPORTED_EXPRESSION);
        return new ComplexityExpr.Unknown("unsupported expression: " + expression.getClass().getSimpleName());
    }

    private ComplexityExpr analyzeMethodCall(
            MethodCallExpr call,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            Map<String, MethodVisitState> visitStates,
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
                            expr -> sizeOrEvaluationCost(
                                    expr, state, methodIndex, memo, visitStates, currentMethod));
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
            return analyzeHelperMethod(methodName, state, methodIndex, memo, visitStates, 0);
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
        state.markTimeIncomplete(StaticAnalysisReasonCode.OPAQUE_CALL);
        return new ComplexityExpr.Unknown("opaque call");
    }

    private ComplexityExpr analyzeHelperMethod(
            String methodName,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            Map<String, MethodVisitState> visitStates,
            int depth) {
        if (depth > MAX_HELPER_ANALYSIS_DEPTH) {
            state.markTimeIncomplete(StaticAnalysisReasonCode.INCOMPLETE_TIME_ANALYSIS);
            return new ComplexityExpr.Unknown("helper depth exceeded");
        }
        MethodVisitState visitState = visitStates.getOrDefault(methodName, MethodVisitState.UNVISITED);
        if (visitState == MethodVisitState.VISITING) {
            state.markTimeIncomplete(StaticAnalysisReasonCode.UNKNOWN_RECURSIVE_CYCLE);
            return new ComplexityExpr.Unknown("recursive cycle");
        }
        if (memo.containsKey(methodName)) {
            return memo.get(methodName);
        }
        MethodDeclaration method = methodIndex.get(methodName);
        if (method == null) {
            state.markTimeIncomplete(StaticAnalysisReasonCode.OPAQUE_CALL);
            return new ComplexityExpr.Unknown("unknown helper");
        }
        visitStates.put(methodName, MethodVisitState.VISITING);
        ComplexityExpr result = analyzeBlock(
                method.getBody().orElse(new BlockStmt()), state, methodIndex, memo, visitStates, methodName, depth + 1);
        visitStates.put(methodName, MethodVisitState.COMPLETE);
        memo.put(methodName, result);
        return result;
    }

    private ComplexityExpr analyzeAuxiliarySpacePeak(
            MethodDeclaration entryMethod,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            Map<String, MethodVisitState> visitStates) {
        ComplexityExpr allocationPeak = analyzeBlockSpacePeak(
                entryMethod.getBody().orElse(new BlockStmt()), state, methodIndex, memo, visitStates, entryMethod.getNameAsString(), false);

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
            Map<String, MethodVisitState> visitStates,
            String currentMethod,
            boolean disjointNestedScope) {
        ComplexityExpr scopeLive = ComplexityExpr.one();
        ComplexityExpr nestedDisjointPeak = ComplexityExpr.one();
        for (Statement statement : block.getStatements()) {
            if (statement instanceof ExpressionStmt exprStmt && exprStmt.getExpression() instanceof VariableDeclarationExpr varDecl) {
                for (var variable : varDecl.getVariables()) {
                    if (variable.getInitializer().isPresent()) {
                        ComplexityExpr alloc = allocationSize(
                                variable.getInitializer().get(), state, methodIndex, memo, visitStates, currentMethod);
                        scopeLive = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(scopeLive, alloc)));
                    }
                }
                continue;
            }
            ComplexityExpr stmtPeak = analyzeStatementSpacePeak(
                    statement, state, methodIndex, memo, visitStates, currentMethod, disjointNestedScope);
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
            Map<String, MethodVisitState> visitStates,
            String currentMethod,
            boolean parentScope) {
        return switch (statement) {
            case BlockStmt block -> analyzeBlockSpacePeak(block, state, methodIndex, memo, visitStates, currentMethod, true);
            case ForStmt forStmt -> {
                ComplexityExpr body = forStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, methodIndex, memo, visitStates, currentMethod, true)
                        : analyzeStatementSpacePeak(forStmt.getBody(), state, methodIndex, memo, visitStates, currentMethod, parentScope);
                yield body;
            }
            case ForEachStmt forEach -> {
                ComplexityExpr body = forEach.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, methodIndex, memo, visitStates, currentMethod, true)
                        : analyzeStatementSpacePeak(forEach.getBody(), state, methodIndex, memo, visitStates, currentMethod, parentScope);
                yield body;
            }
            case WhileStmt whileStmt -> {
                ComplexityExpr body = whileStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, methodIndex, memo, visitStates, currentMethod, true)
                        : analyzeStatementSpacePeak(whileStmt.getBody(), state, methodIndex, memo, visitStates, currentMethod, parentScope);
                yield body;
            }
            case ReturnStmt returnStmt -> returnStmt.getExpression()
                    .map(expr -> allocationSize(expr, state, methodIndex, memo, visitStates, currentMethod))
                    .orElse(ComplexityExpr.one());
            case ExpressionStmt expressionStmt -> expressionStmt.getExpression() == null
                    ? ComplexityExpr.one()
                    : allocationSize(expressionStmt.getExpression(), state, methodIndex, memo, visitStates, currentMethod);
            case IfStmt ifStmt -> {
                ComplexityExpr thenPeak = ifStmt.getThenStmt() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, methodIndex, memo, visitStates, currentMethod, true)
                        : analyzeStatementSpacePeak(ifStmt.getThenStmt(), state, methodIndex, memo, visitStates, currentMethod, parentScope);
                ComplexityExpr elsePeak = ifStmt.getElseStmt()
                        .map(s -> s instanceof BlockStmt b
                                ? analyzeBlockSpacePeak(b, state, methodIndex, memo, visitStates, currentMethod, true)
                                : analyzeStatementSpacePeak(s, state, methodIndex, memo, visitStates, currentMethod, parentScope))
                        .orElse(ComplexityExpr.one());
                yield ComplexityExprSimplifier.branchWorstCase(thenPeak, elsePeak);
            }
            default -> {
                state.markSpaceIncomplete(StaticAnalysisReasonCode.UNSUPPORTED_STATEMENT);
                yield new ComplexityExpr.Unknown("unsupported statement for space");
            }
        };
    }

    private ComplexityExpr allocationSize(
            Expression expression,
            AnalysisState state,
            Map<String, MethodDeclaration> methodIndex,
            Map<String, ComplexityExpr> memo,
            Map<String, MethodVisitState> visitStates,
            String currentMethod) {
        if (expression instanceof ArrayCreationExpr array && !array.getLevels().isEmpty()
                && array.getLevels().get(0).getDimension().isPresent()) {
            return sizeOrEvaluationCost(
                    array.getLevels().get(0).getDimension().get(), state, methodIndex, memo, visitStates, currentMethod);
        }
        if (expression instanceof ObjectCreationExpr creation) {
            if (creation.getArguments().isEmpty()) {
                return ComplexityExpr.one();
            }
            return sizeOrEvaluationCost(
                    creation.getArguments().get(0), state, methodIndex, memo, visitStates, currentMethod);
        }
        if (expression instanceof VariableDeclarationExpr varDecl) {
            ComplexityExpr total = ComplexityExpr.one();
            for (VariableDeclarator variable : varDecl.getVariables()) {
                if (variable.getInitializer().isPresent()) {
                    total = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                            total,
                            allocationSize(variable.getInitializer().get(), state, methodIndex, memo, visitStates, currentMethod))));
                }
            }
            return total;
        }
        if (expression instanceof AssignExpr assign) {
            return allocationSize(assign.getValue(), state, methodIndex, memo, visitStates, currentMethod);
        }
        return ComplexityExpr.one();
    }

    private enum MethodVisitState {
        UNVISITED,
        VISITING,
        COMPLETE
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
        final EnumSet<StaticAnalysisReasonCode> reasonCodes = EnumSet.noneOf(StaticAnalysisReasonCode.class);
        AnalysisDimensionCompleteness timeCompleteness = AnalysisDimensionCompleteness.COMPLETE;
        AnalysisDimensionCompleteness spaceCompleteness = AnalysisDimensionCompleteness.COMPLETE;
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

        void markUnknownLoop(StaticAnalysisReasonCode code) {
            unknownLoop = true;
            markTimeIncomplete(code);
            limitations.add(code.name());
        }

        void markTimeIncomplete(StaticAnalysisReasonCode code) {
            timeCompleteness = AnalysisDimensionCompleteness.INCOMPLETE;
            reasonCodes.add(code);
            limitations.add(code.name());
        }

        void markSpaceIncomplete(StaticAnalysisReasonCode code) {
            spaceCompleteness = AnalysisDimensionCompleteness.INCOMPLETE;
            reasonCodes.add(code);
            limitations.add(code.name());
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
