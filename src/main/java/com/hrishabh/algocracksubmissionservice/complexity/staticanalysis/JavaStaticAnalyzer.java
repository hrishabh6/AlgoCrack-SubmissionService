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
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context.ParameterShapeRegistry;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context.ParameterVariableMapper;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.context.SymbolSizeResolver;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExprSimplifier;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkCallTarget;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkCallTargetResolver;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeEntry;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.loop.ConservativeLoopBoundProof;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.AnalysisDimensionCompleteness;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisReasonCode;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural.HelperCallSiteSupport;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural.InterproceduralContext;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural.MethodCallGraph;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural.MethodIdentity;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural.UserMethodIndex;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural.UserMethodOverloadResolver;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.recurrence.DirectRecurrenceAnalyzer;
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

    public static final String ANALYZER_VERSION = "static-v2.2-dev";
    public static final String CONFIDENCE_MODEL_VERSION = "static-confidence-v2.1-dev";

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

            UserMethodIndex userMethods = UserMethodIndex.build(unit);
            InterproceduralContext ipc = new InterproceduralContext(userMethods);
            String declaringTypeName = entryMethod.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(ClassOrInterfaceDeclaration::getNameAsString)
                    .orElse("Solution");
            MethodIdentity entryIdentity = MethodIdentity.of(entryMethod, declaringTypeName);
            state.setAnalysisContext(declaringTypeName, entryIdentity);
            ComplexityExpr time = analyzeBlock(entryMethod.getBody().orElse(new BlockStmt()), state, ipc, entryIdentity, 0);
            ComplexityExpr space = analyzeAuxiliarySpacePeak(entryMethod, state, ipc, entryIdentity);

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
        validateDocumentedSymbols(state, rawTime, true);
        validateDocumentedSymbols(state, rawSpace, false);

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

    private static void validateDocumentedSymbols(AnalysisState state, ComplexityExpr expr, boolean timeAxis) {
        if (expr == null || expr instanceof ComplexityExpr.Unknown) {
            return;
        }
        ComplexityExpr simplified = ComplexityExprSimplifier.simplify(expr);
        if (!ComplexityExprSimplifier.allVariablesDocumented(simplified, state.documentedSymbolKeys())) {
            if (timeAxis) {
                state.markTimeIncomplete(StaticAnalysisReasonCode.UNDEFINED_SYMBOL);
            } else {
                state.markSpaceIncomplete(StaticAnalysisReasonCode.UNDEFINED_SYMBOL);
            }
        }
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


    private ComplexityExpr analyzeBlock(
            BlockStmt block,
            AnalysisState state,
            InterproceduralContext ipc, MethodIdentity currentMethod,
            int depth) {
        List<ComplexityExpr> sequential = new ArrayList<>();
        for (Statement statement : block.getStatements()) {
            sequential.add(analyzeStatement(statement, state, ipc, currentMethod, depth));
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
            InterproceduralContext ipc, MethodIdentity currentMethod,
            int depth) {
        return switch (statement) {
            case BlockStmt block -> analyzeBlock(block, state, ipc, currentMethod, depth);
            case IfStmt ifStmt -> {
                ComplexityExpr conditionCost = analyzeExpression(
                        ifStmt.getCondition(), state, ipc, currentMethod);
                ComplexityExpr thenCost = ifStmt.getThenStmt() instanceof BlockStmt b
                        ? analyzeBlock(b, state, ipc, currentMethod, depth)
                        : analyzeStatement(ifStmt.getThenStmt(), state, ipc, currentMethod, depth);
                ComplexityExpr elseCost = ifStmt.getElseStmt()
                        .map(s -> s instanceof BlockStmt b
                                ? analyzeBlock(b, state, ipc, currentMethod, depth)
                                : analyzeStatement(s, state, ipc, currentMethod, depth))
                        .orElse(ComplexityExpr.one());
                ComplexityExpr branch = ComplexityExprSimplifier.branchWorstCase(thenCost, elseCost);
                yield ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(conditionCost, branch)));
            }
            case ForStmt forStmt -> analyzeForLoop(forStmt, state, ipc, currentMethod, depth);
            case ForEachStmt forEach -> analyzeForEach(forEach, state, ipc, currentMethod, depth);
            case WhileStmt whileStmt -> analyzeWhile(whileStmt, state, ipc, currentMethod, depth);
            case DoStmt doStmt -> {
                state.markUnknownLoop(StaticAnalysisReasonCode.LOOP_PROGRESS_NOT_PROVEN);
                ComplexityExpr body = doStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlock(b, state, ipc, currentMethod, depth + 1)
                        : analyzeStatement(doStmt.getBody(), state, ipc, currentMethod, depth + 1);
                yield new ComplexityExpr.Unknown("do-while");
            }
            case SwitchStmt switchStmt -> {
                ComplexityExpr selector = analyzeExpression(
                        switchStmt.getSelector(), state, ipc, currentMethod);
                ComplexityExpr armWorst = ComplexityExpr.one();
                for (SwitchEntry entry : switchStmt.getEntries()) {
                    ComplexityExpr entryCost = ComplexityExpr.one();
                    for (Statement inner : entry.getStatements()) {
                        entryCost = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                                entryCost,
                                analyzeStatement(inner, state, ipc, currentMethod, depth))));
                    }
                    armWorst = ComplexityExprSimplifier.branchWorstCase(armWorst, entryCost);
                }
                yield ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(selector, armWorst)));
            }
            case TryStmt tryStmt -> {
                List<ComplexityExpr> parts = new ArrayList<>();
                parts.add(analyzeBlock(tryStmt.getTryBlock(), state, ipc, currentMethod, depth));
                for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                    parts.add(analyzeBlock(catchClause.getBody(), state, ipc, currentMethod, depth));
                }
                tryStmt.getFinallyBlock().ifPresent(finallyBlock ->
                        parts.add(analyzeBlock(finallyBlock, state, ipc, currentMethod, depth)));
                ComplexityExpr tryWorst = ComplexityExpr.one();
                for (ComplexityExpr part : parts) {
                    tryWorst = ComplexityExprSimplifier.branchWorstCase(tryWorst, part);
                }
                yield ComplexityExprSimplifier.simplify(tryWorst);
            }
            case ReturnStmt returnStmt -> returnStmt.getExpression()
                    .map(expr -> analyzeExpression(expr, state, ipc, currentMethod))
                    .orElse(ComplexityExpr.one());
            case ExpressionStmt expressionStmt -> {
                if (expressionStmt.getExpression() == null) {
                    yield ComplexityExpr.one();
                }
                yield analyzeExpression(expressionStmt.getExpression(), state, ipc, currentMethod);
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
            InterproceduralContext ipc, MethodIdentity currentMethod,
            int depth) {
        ConservativeLoopBoundProof.BoundMapper mapper = boundMapper(state);
        ConservativeLoopBoundProof.ProofResult proof = ConservativeLoopBoundProof.proveForLoop(forStmt, mapper);
        ComplexityExpr body = forStmt.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, ipc, currentMethod, depth + 1)
                : analyzeStatement(forStmt.getBody(), state, ipc, currentMethod, depth + 1);
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
            InterproceduralContext ipc, MethodIdentity currentMethod,
            int depth) {
        ComplexityExpr iterableSize = resolveIterableSize(forEach.getIterable(), state);
        ComplexityExpr body = forEach.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, ipc, currentMethod, depth + 1)
                : analyzeStatement(forEach.getBody(), state, ipc, currentMethod, depth + 1);
        if (iterableSize instanceof ComplexityExpr.Unknown) {
            state.markUnknownLoop(StaticAnalysisReasonCode.UNKNOWN_LOOP_BOUND);
        }
        return new ComplexityExpr.Product(List.of(iterableSize, body));
    }

    private ComplexityExpr analyzeWhile(
            WhileStmt whileStmt,
            AnalysisState state,
            InterproceduralContext ipc, MethodIdentity currentMethod,
            int depth) {
        ConservativeLoopBoundProof.BoundMapper mapper = boundMapper(state);
        ConservativeLoopBoundProof.ProofResult proof = ConservativeLoopBoundProof.proveWhileLoop(whileStmt, mapper);
        ComplexityExpr body = whileStmt.getBody() instanceof BlockStmt b
                ? analyzeBlock(b, state, ipc, currentMethod, depth + 1)
                : analyzeStatement(whileStmt.getBody(), state, ipc, currentMethod, depth + 1);
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
            InterproceduralContext ipc, MethodIdentity currentMethod) {
        if (expression != null) {
            Optional<ComplexityExpr> sized = mapSizeExpression(expression, state);
            if (sized.isPresent()) {
                return sized.get();
            }
        }
        return analyzeExpression(expression, state, ipc, currentMethod);
    }

    private ComplexityExpr resolveIterableSize(Expression iterable, AnalysisState state) {
        return mapSizeExpression(iterable, state).orElse(new ComplexityExpr.Unknown("iterable size"));
    }

    private Optional<ComplexityExpr> mapSizeExpression(Expression expression, AnalysisState state) {
        return state.sizeResolver.resolveSize(expression);
    }

    private static void registerLocalSizeAlias(String localName, Expression initializer, AnalysisState state) {
        Optional<ComplexityExpr> size = state.sizeResolver.resolveSize(initializer);
        if (size.isPresent()) {
            state.sizeResolver.bindLocalAlias(localName, size.get());
            return;
        }
        if (initializer instanceof NameExpr name) {
            state.sizeResolver.resolveSize(name).ifPresent(s -> state.sizeResolver.bindLocalAlias(localName, s));
            return;
        }
        state.sizeResolver.invalidateLocal(localName);
    }

    private ComplexityExpr analyzeExpression(
            Expression expression,
            AnalysisState state,
            InterproceduralContext ipc, MethodIdentity currentMethod) {
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
            return analyzeExpression(enclosed.getInner(), state, ipc, currentMethod);
        }
        if (expression instanceof UnaryExpr unary) {
            return analyzeExpression(unary.getExpression(), state, ipc, currentMethod);
        }
        if (expression instanceof BinaryExpr binary) {
            if (binary.getOperator() == BinaryExpr.Operator.PLUS) {
                return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                        analyzeExpression(binary.getLeft(), state, ipc, currentMethod),
                        analyzeExpression(binary.getRight(), state, ipc, currentMethod))));
            }
            if (binary.getOperator() == BinaryExpr.Operator.MULTIPLY) {
                return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                        analyzeExpression(binary.getLeft(), state, ipc, currentMethod),
                        analyzeExpression(binary.getRight(), state, ipc, currentMethod),
                        ComplexityExpr.one())));
            }
            if (isPureEvaluationBinary(binary.getOperator())) {
                return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                        analyzeExpression(binary.getLeft(), state, ipc, currentMethod),
                        analyzeExpression(binary.getRight(), state, ipc, currentMethod))));
            }
            return unsupportedExpression(expression, state);
        }
        if (expression instanceof ConditionalExpr conditional) {
            ComplexityExpr condition = analyzeExpression(
                    conditional.getCondition(), state, ipc, currentMethod);
            ComplexityExpr thenExpr = analyzeExpression(
                    conditional.getThenExpr(), state, ipc, currentMethod);
            ComplexityExpr elseExpr = analyzeExpression(
                    conditional.getElseExpr(), state, ipc, currentMethod);
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                    condition, ComplexityExprSimplifier.branchWorstCase(thenExpr, elseExpr))));
        }
        if (expression instanceof VariableDeclarationExpr varDecl) {
            ComplexityExpr total = ComplexityExpr.one();
            for (VariableDeclarator variable : varDecl.getVariables()) {
                state.registerLocalType(variable.getNameAsString(), varDecl.getElementType().asString());
                if (variable.getInitializer().isPresent()) {
                    Expression init = variable.getInitializer().get();
                    registerLocalSizeAlias(variable.getNameAsString(), init, state);
                    total = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                            total,
                            analyzeExpression(init, state, ipc, currentMethod))));
                }
            }
            return total;
        }
        if (expression instanceof AssignExpr assign) {
            if (assign.getTarget() instanceof NameExpr name) {
                registerLocalSizeAlias(name.getNameAsString(), assign.getValue(), state);
            }
            ComplexityExpr target = assign.getTarget() instanceof Expression targetExpr
                    ? analyzeExpression(targetExpr, state, ipc, currentMethod)
                    : ComplexityExpr.one();
            ComplexityExpr value = analyzeExpression(
                    assign.getValue(), state, ipc, currentMethod);
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(target, value)));
        }
        if (expression instanceof MethodCallExpr call) {
            return analyzeMethodCall(call, state, ipc, currentMethod);
        }
        if (expression instanceof ObjectCreationExpr creation) {
            ComplexityExpr size = creation.getArguments().isEmpty()
                    ? ComplexityExpr.one()
                    : analyzeExpression(creation.getArguments().get(0), state, ipc, currentMethod);
            state.addFinding("ALLOCATION", creation.getBegin().map(p -> p.line).orElse(null),
                    creation.getEnd().map(p -> p.line).orElse(null),
                    ComplexityExprSimplifier.toExpressionString(size), "MEDIUM",
                    "Object allocation " + creation.getType().getNameAsString());
            return size;
        }
        if (expression instanceof ArrayCreationExpr arrayCreation && !arrayCreation.getLevels().isEmpty()) {
            ComplexityExpr magnitude = arrayAllocationMagnitude(
                    arrayCreation, state, ipc, currentMethod);
            state.addFinding("ALLOCATION", arrayCreation.getBegin().map(p -> p.line).orElse(null),
                    arrayCreation.getEnd().map(p -> p.line).orElse(null),
                    ComplexityExprSimplifier.toExpressionString(magnitude), "MEDIUM", "Array allocation");
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(magnitude, ComplexityExpr.one())));
        }
        if (expression instanceof CastExpr cast) {
            return analyzeExpression(cast.getExpression(), state, ipc, currentMethod);
        }
        if (expression instanceof ArrayAccessExpr access) {
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                    analyzeExpression(access.getName(), state, ipc, currentMethod),
                    analyzeExpression(access.getIndex(), state, ipc, currentMethod))));
        }
        if (expression instanceof FieldAccessExpr access) {
            ComplexityExpr scopeCost = analyzeExpression(
                    access.getScope(), state, ipc, currentMethod);
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(scopeCost, ComplexityExpr.one())));
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
            InterproceduralContext ipc,
            MethodIdentity currentMethod) {
        String methodName = call.getNameAsString();
        if (call.getScope().isEmpty() && !ipc.userMethods.candidatesInType(state.declaringTypeName, methodName).isEmpty()) {
            UserMethodOverloadResolver.Resolution resolution = UserMethodOverloadResolver.resolveUnqualifiedCall(
                    call,
                    state.declaringTypeName,
                    ipc.userMethods,
                    state.localNameToType,
                    state.paramTypes);
            if (resolution.outcome() == UserMethodOverloadResolver.ResolutionOutcome.AMBIGUOUS) {
                state.markTimeIncomplete(StaticAnalysisReasonCode.AMBIGUOUS_METHOD_TARGET);
                return new ComplexityExpr.Unknown("ambiguous method target");
            }
            if (resolution.outcome() == UserMethodOverloadResolver.ResolutionOutcome.UNRESOLVED) {
                state.markTimeIncomplete(StaticAnalysisReasonCode.UNRESOLVED_HELPER_TARGET);
                return new ComplexityExpr.Unknown("unresolved helper target");
            }
            MethodIdentity target = resolution.identity();
            ipc.callGraph.addEdge(currentMethod, target);
            MethodCallGraph.RecursionKind recursionKind = ipc.callGraph.classify(target);
            if (recursionKind == MethodCallGraph.RecursionKind.RECURSIVE_SCC && !target.equals(currentMethod)) {
                state.markTimeIncomplete(StaticAnalysisReasonCode.UNSUPPORTED_RECURSIVE_SCC);
                return new ComplexityExpr.Unknown("unsupported recursive scc");
            }
            if (target.equals(currentMethod)) {
                return analyzeDirectSelfRecurrence(call, state, ipc, currentMethod, resolution.declaration());
            }
            return analyzeResolvedHelperCall(call, state, ipc, currentMethod, target, resolution.declaration(), 0);
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
            ComplexityExpr jdkTime = entry.timeExpression();
            if (!ComplexityExprSimplifier.allVariablesDocumented(jdkTime, state.documentedSymbolKeys())) {
                state.markTimeIncomplete(StaticAnalysisReasonCode.UNDEFINED_SYMBOL);
            }
            return jdkTime;
        }
        String scopeLabel = call.getScope().map(Object::toString).orElse("unknown");
        state.markOpaque("Unresolved external call: " + scopeLabel + "." + methodName);
        state.addFinding("OPAQUE_CALL", call.getBegin().map(p -> p.line).orElse(null),
                call.getEnd().map(p -> p.line).orElse(null), null, "LOW",
                "Unresolved call " + scopeLabel + "." + methodName + " — not treated as O(1)");
        state.markTimeIncomplete(StaticAnalysisReasonCode.OPAQUE_CALL);
        return new ComplexityExpr.Unknown("opaque call");
    }

    private ComplexityExpr analyzeDirectSelfRecurrence(
            MethodCallExpr call,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            MethodDeclaration method) {
        String recurrenceKey = currentMethod.cacheKey() + "#recurrence";
        if (ipc.recurrenceMemo.containsKey(recurrenceKey)) {
            ComplexityExpr cached = ipc.recurrenceMemo.get(recurrenceKey);
            if (cached instanceof ComplexityExpr.Unknown u && "recurrence pending".equals(u.reason())) {
                return ComplexityExpr.one();
            }
            return cached;
        }
        {
            ipc.recurrenceMemo.put(recurrenceKey, new ComplexityExpr.Unknown("recurrence pending"));
            String sizeParam = primarySizeParameterName(method).orElse("n");
            AnalysisState siblingState = state.forkForRecurrenceSiblingPass();
            Optional<DirectRecurrenceAnalyzer.DirectRecurrenceResult> direct = DirectRecurrenceAnalyzer.analyze(
                    method,
                    currentMethod,
                    sizeParam,
                    stmt -> analyzeStatement(stmt, siblingState, ipc, currentMethod, 0),
                    expr -> sizeOrEvaluationCost(expr, siblingState, ipc, currentMethod));
            ComplexityExpr resolved;
            if (direct.isEmpty()) {
                state.markTimeIncomplete(StaticAnalysisReasonCode.UNSUPPORTED_RECURRENCE);
                state.markOpaque("Recurrence pattern not supported");
                resolved = new ComplexityExpr.Unknown("unsupported recurrence");
            } else {
                DirectRecurrenceAnalyzer.DirectRecurrenceResult dr = direct.get();
                String sizeVar = state.paramNameToSymbol.getOrDefault(sizeParam, state.primarySizeVariable());
                resolved = RecurrenceSupport.totalTime(dr.pattern(), dr.siblingWorkPerLevel(), sizeVar);
                if (resolved instanceof ComplexityExpr.Unknown) {
                    state.markTimeIncomplete(StaticAnalysisReasonCode.UNSUPPORTED_RECURRENCE);
                    state.markOpaque("Recurrence pattern not supported");
                }
            }
            ipc.recurrenceMemo.put(recurrenceKey, resolved);
        }
        ComplexityExpr recurrence = ipc.recurrenceMemo.get(recurrenceKey);
        state.addFinding("RECURSION", call.getBegin().map(p -> p.line).orElse(null),
                call.getEnd().map(p -> p.line).orElse(null),
                ComplexityExprSimplifier.toExpressionString(recurrence), "MEDIUM",
                "Direct recursion detected");
        return recurrence;
    }

    private ComplexityExpr analyzeResolvedHelperCall(
            MethodCallExpr call,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity caller,
            MethodIdentity target,
            MethodDeclaration method,
            int depth) {
        if (depth > MAX_HELPER_ANALYSIS_DEPTH) {
            state.markTimeIncomplete(StaticAnalysisReasonCode.INCOMPLETE_TIME_ANALYSIS);
            return new ComplexityExpr.Unknown("helper depth exceeded");
        }
        InterproceduralContext.MethodVisitState visitState =
                ipc.visitStates.getOrDefault(target, InterproceduralContext.MethodVisitState.UNVISITED);
        if (visitState == InterproceduralContext.MethodVisitState.VISITING) {
            state.markTimeIncomplete(StaticAnalysisReasonCode.UNKNOWN_RECURSIVE_CYCLE);
            return new ComplexityExpr.Unknown("recursive cycle");
        }
        String summaryKey = target.cacheKey() + "#summary";
        ComplexityExpr helperSummary = ipc.helperSummaryMemo.get(summaryKey);
        if (helperSummary == null) {
            ipc.visitStates.put(target, InterproceduralContext.MethodVisitState.VISITING);
            AnalysisState helperState = state.forkForHelper(method);
            helperSummary = analyzeBlock(
                    method.getBody().orElse(new BlockStmt()), helperState, ipc, target, depth + 1);
            ipc.visitStates.put(target, InterproceduralContext.MethodVisitState.COMPLETE);
            if (helperSummary instanceof ComplexityExpr.Unknown) {
                state.markTimeIncomplete(StaticAnalysisReasonCode.HELPER_SUMMARY_INCOMPLETE);
            }
            ipc.helperSummaryMemo.put(summaryKey, helperSummary);
        }
        HelperCallSiteSupport.SubstitutionResult substitution = HelperCallSiteSupport.substituteAtCallSite(
                helperSummary,
                method,
                call,
                arg -> mapSizeExpression(arg, state),
                state.documentedSymbolKeys());
        if (!substitution.success()) {
            state.markTimeIncomplete(StaticAnalysisReasonCode.CALLSITE_SUBSTITUTION_FAILED);
            return new ComplexityExpr.Unknown("call-site substitution failed");
        }
        return substitution.expr();
    }

    private static Optional<String> primarySizeParameterName(MethodDeclaration method) {
        for (Parameter parameter : method.getParameters()) {
            String type = MethodIdentity.eraseType(parameter.getType().asString());
            if ("int".equals(type) || "long".equals(type)) {
                return Optional.of(parameter.getNameAsString());
            }
        }
        if (!method.getParameters().isEmpty()) {
            return Optional.of(method.getParameters().get(0).getNameAsString());
        }
        return Optional.empty();
    }

    private ComplexityExpr analyzeAuxiliarySpacePeak(
            MethodDeclaration entryMethod,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity entryIdentity) {
        ComplexityExpr allocationPeak = analyzeBlockSpacePeak(
                entryMethod.getBody().orElse(new BlockStmt()), state, ipc, entryIdentity, false);

        MethodCallGraph.RecursionKind kind = ipc.callGraph.classify(entryIdentity);
        ArgumentPattern stackPattern = ArgumentPattern.UNSUPPORTED;
        if (kind == MethodCallGraph.RecursionKind.DIRECT_SELF_RECURSION) {
            stackPattern = RecurrenceSupport.unifiedStackPatternFromIdentity(
                    entryMethod, entryIdentity);
        }
        ComplexityExpr stack = RecurrenceSupport.recursionStackDepth(stackPattern, state.primarySizeVariable());
        if (stackPattern == ArgumentPattern.UNSUPPORTED
                && !DirectRecurrenceAnalyzer.directSelfCalls(entryMethod, entryIdentity).isEmpty()) {
            stack = new ComplexityExpr.Unknown("recursion stack depth unknown");
        }

        return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(allocationPeak, stack)));
    }

    private ComplexityExpr analyzeBlockSpacePeak(
            BlockStmt block,
            AnalysisState state,
            InterproceduralContext ipc, MethodIdentity currentMethod,
            boolean disjointNestedScope) {
        ComplexityExpr scopeLive = ComplexityExpr.one();
        ComplexityExpr nestedDisjointPeak = ComplexityExpr.one();
        for (Statement statement : block.getStatements()) {
            if (statement instanceof ExpressionStmt exprStmt && exprStmt.getExpression() instanceof VariableDeclarationExpr varDecl) {
                for (var variable : varDecl.getVariables()) {
                    if (variable.getInitializer().isPresent()) {
                        registerLocalSizeAlias(variable.getNameAsString(), variable.getInitializer().get(), state);
                        ComplexityExpr alloc = allocationSize(
                                variable.getInitializer().get(), state, ipc, currentMethod);
                        scopeLive = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(scopeLive, alloc)));
                    }
                }
                continue;
            }
            ComplexityExpr stmtPeak = analyzeStatementSpacePeak(
                    statement, state, ipc, currentMethod, disjointNestedScope);
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
            InterproceduralContext ipc, MethodIdentity currentMethod,
            boolean parentScope) {
        return switch (statement) {
            case BlockStmt block -> analyzeBlockSpacePeak(block, state, ipc, currentMethod, true);
            case ForStmt forStmt -> {
                ComplexityExpr body = forStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, true)
                        : analyzeStatementSpacePeak(forStmt.getBody(), state, ipc, currentMethod, parentScope);
                yield body;
            }
            case ForEachStmt forEach -> {
                ComplexityExpr body = forEach.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, true)
                        : analyzeStatementSpacePeak(forEach.getBody(), state, ipc, currentMethod, parentScope);
                yield body;
            }
            case WhileStmt whileStmt -> {
                ComplexityExpr body = whileStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, true)
                        : analyzeStatementSpacePeak(whileStmt.getBody(), state, ipc, currentMethod, parentScope);
                yield body;
            }
            case ReturnStmt returnStmt -> returnStmt.getExpression()
                    .map(expr -> allocationSize(expr, state, ipc, currentMethod))
                    .orElse(ComplexityExpr.one());
            case ExpressionStmt expressionStmt -> expressionStmt.getExpression() == null
                    ? ComplexityExpr.one()
                    : allocationSize(expressionStmt.getExpression(), state, ipc, currentMethod);
            case IfStmt ifStmt -> {
                ComplexityExpr thenPeak = ifStmt.getThenStmt() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, true)
                        : analyzeStatementSpacePeak(ifStmt.getThenStmt(), state, ipc, currentMethod, parentScope);
                ComplexityExpr elsePeak = ifStmt.getElseStmt()
                        .map(s -> s instanceof BlockStmt b
                                ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, true)
                                : analyzeStatementSpacePeak(s, state, ipc, currentMethod, parentScope))
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
            InterproceduralContext ipc, MethodIdentity currentMethod) {
        if (expression instanceof ArrayCreationExpr array && !array.getLevels().isEmpty()) {
            return arrayAllocationMagnitude(array, state, ipc, currentMethod);
        }
        if (expression instanceof ObjectCreationExpr creation) {
            if (creation.getArguments().isEmpty()) {
                return ComplexityExpr.one();
            }
            return sizeOrEvaluationCost(
                    creation.getArguments().get(0), state, ipc, currentMethod);
        }
        if (expression instanceof VariableDeclarationExpr varDecl) {
            ComplexityExpr total = ComplexityExpr.one();
            for (VariableDeclarator variable : varDecl.getVariables()) {
                if (variable.getInitializer().isPresent()) {
                    total = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                            total,
                            allocationSize(variable.getInitializer().get(), state, ipc, currentMethod))));
                }
            }
            return total;
        }
        if (expression instanceof AssignExpr assign) {
            return allocationSize(assign.getValue(), state, ipc, currentMethod);
        }
        return ComplexityExpr.one();
    }

    private ComplexityExpr arrayAllocationMagnitude(
            ArrayCreationExpr array,
            AnalysisState state,
            InterproceduralContext ipc, MethodIdentity currentMethod) {
        List<ComplexityExpr> dimensions = new ArrayList<>();
        for (var level : array.getLevels()) {
            if (level.getDimension().isEmpty()) {
                continue;
            }
            ComplexityExpr dim = sizeOrEvaluationCost(
                    level.getDimension().get(), state, ipc, currentMethod);
            if (dim instanceof ComplexityExpr.Unknown) {
                return dim;
            }
            dimensions.add(dim);
        }
        if (dimensions.isEmpty()) {
            return ComplexityExpr.one();
        }
        if (dimensions.size() == 1) {
            return dimensions.getFirst();
        }
        return ComplexityExprSimplifier.simplify(new ComplexityExpr.Product(dimensions));
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
        final SymbolSizeResolver sizeResolver;
        final List<ParameterShapeRegistry.ParameterShape> parameterShapes;
        final EnumSet<StaticAnalysisReasonCode> reasonCodes = EnumSet.noneOf(StaticAnalysisReasonCode.class);
        final Map<String, String> localNameToType = new HashMap<>();
        String declaringTypeName = "Solution";
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
            this.parameterShapes = buildParameterShapes(metadata, entryMethod);
            this.paramNameToSymbol = buildParamNameToSymbol(parameterShapes);
            this.sizeResolver = new SymbolSizeResolver(parameterShapes);
        }

        void setAnalysisContext(String declaringTypeName, MethodIdentity entryIdentity) {
            this.declaringTypeName = declaringTypeName;
        }

        void registerLocalType(String localName, String rawType) {
            if (localName == null || localName.isBlank() || rawType == null) {
                return;
            }
            localNameToType.put(localName, MethodIdentity.eraseType(rawType));
        }

        AnalysisState forkForRecurrenceSiblingPass() {
            AnalysisState fork = new AnalysisState(
                    findings,
                    limitations,
                    variables,
                    compilationUnit,
                    null,
                    parameterShapes,
                    paramTypes);
            fork.declaringTypeName = declaringTypeName;
            fork.localNameToType.putAll(localNameToType);
            return fork;
        }

        AnalysisState forkForHelper(MethodDeclaration helper) {
            List<ParameterShapeRegistry.ParameterShape> shapes =
                    ParameterShapeRegistry.shapesFromParameters(helper.getParameters());
            Map<String, String> helperVars = ParameterShapeRegistry.documentedVariables(shapes);
            AnalysisState fork = new AnalysisState(
                    findings,
                    limitations,
                    helperVars,
                    compilationUnit,
                    helper,
                    shapes,
                    buildDeclaredTypes(compilationUnit, helper));
            fork.declaringTypeName = declaringTypeName;
            return fork;
        }

        private AnalysisState(
                List<StaticFindingDraft> findings,
                List<String> limitations,
                Map<String, String> variables,
                CompilationUnit compilationUnit,
                MethodDeclaration methodForParams,
                List<ParameterShapeRegistry.ParameterShape> shapes,
                Map<String, String> declaredParamTypes) {
            this.findings = findings;
            this.limitations = limitations;
            this.variables = variables;
            this.compilationUnit = compilationUnit;
            this.paramTypes = declaredParamTypes;
            this.parameterShapes = shapes;
            this.paramNameToSymbol = buildParamNameToSymbol(parameterShapes);
            this.sizeResolver = new SymbolSizeResolver(parameterShapes);
        }

        Set<String> documentedSymbolKeys() {
            return variables.keySet();
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

    }

    private static List<ParameterShapeRegistry.ParameterShape> buildParameterShapes(
            QuestionMetadataApiDto metadata, MethodDeclaration entryMethod) {
        if (metadata != null && metadata.getParamNames() != null && metadata.getParamTypes() != null) {
            return ParameterShapeRegistry.shapesFromMetadata(metadata.getParamNames(), metadata.getParamTypes());
        }
        return ParameterShapeRegistry.shapesFromParameters(entryMethod.getParameters());
    }

    private static Map<String, String> buildParamNameToSymbol(
            List<ParameterShapeRegistry.ParameterShape> shapes) {
        Map<String, String> mapping = new HashMap<>();
        for (ParameterShapeRegistry.ParameterShape shape : shapes) {
            mapping.put(shape.paramName(), shape.primarySymbol());
        }
        return mapping;
    }
}
