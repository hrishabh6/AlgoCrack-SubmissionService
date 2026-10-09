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
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkCallSignatureInference;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkCallSiteBinder;
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
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasisMerge;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticFindingDraft;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.space.CollectionGrowthSpaceSupport;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.space.MethodSpaceSummary;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.space.ReturnAllocationSupport;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.worklist.WorklistStructuralProof;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class JavaStaticAnalyzer {

    public static final String ANALYZER_VERSION = "static-v2.5-batch5-worklist";
    public static final String CONFIDENCE_MODEL_VERSION = "static-confidence-v2.3-batch5-worklist";

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
                || state.hasTimeBlockingReasonCodes()
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
        if (state.timeCompleteness == AnalysisDimensionCompleteness.INCOMPLETE || state.hasTimeBlockingReasonCodes()) {
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
        if (state.timeCompleteness == AnalysisDimensionCompleteness.INCOMPLETE || state.hasTimeBlockingReasonCodes()) {
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
                if (isMutableStaticField(field, variable)) {
                    state.addFinding("MUTABLE_STATIC", field.getBegin().map(p -> p.line).orElse(null),
                            field.getEnd().map(p -> p.line).orElse(null), null, "HIGH",
                            "Mutable static field " + variable.getNameAsString());
                    state.markSpaceIncomplete(StaticAnalysisReasonCode.MUTABLE_STATIC_STATE);
                }
            }
        }
    }

    private static boolean isMutableStaticField(FieldDeclaration field, VariableDeclarator variable) {
        if (variable.getInitializer().isEmpty() && !field.isFinal()) {
            return true;
        }
        if (variable.getInitializer().isEmpty()) {
            return false;
        }
        String erased = MethodIdentity.eraseType(field.getElementType().asString());
        if ("int".equals(erased) || "long".equals(erased) || "boolean".equals(erased) || "double".equals(erased)
                || "float".equals(erased) || "char".equals(erased) || "byte".equals(erased) || "short".equals(erased)) {
            return !field.isFinal();
        }
        if ("String".equals(erased) && field.isFinal()) {
            return false;
        }
        return true;
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
        WorklistStructuralProof.Attempt structural = WorklistStructuralProof.attempt(whileStmt, worklistLookup(state));
        if (structural.recognized()) {
            return analyzeProvenOrRejectedWorklist(whileStmt, structural, state, ipc, currentMethod, depth);
        }
        ConservativeLoopBoundProof.BoundMapper mapper = boundMapper(state);
        ConservativeLoopBoundProof.ProofResult proof = ConservativeLoopBoundProof.proveWhileLoop(whileStmt, mapper);
        ComplexityExpr body = whileBodyCost(whileStmt, state, ipc, currentMethod, depth);
        if (proof.failure().isPresent()) {
            state.markUnknownLoop(proof.failure().get());
            return new ComplexityExpr.Unknown("loop");
        }
        ComplexityExpr bound = proof.iterationBound().orElseThrow();
        return new ComplexityExpr.Product(List.of(bound, body));
    }

    private ComplexityExpr analyzeProvenOrRejectedWorklist(
            WhileStmt whileStmt,
            WorklistStructuralProof.Attempt structural,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            int depth) {
        if (structural.proof().isEmpty()) {
            state.markUnknownLoop(structural.failure());
            whileBodyCost(whileStmt, state, ipc, currentMethod, depth);
            return new ComplexityExpr.Unknown("worklist");
        }
        WorklistStructuralProof.Proof proof = structural.proof().get();
        state.structuralCardinality.put(proof.containerName(), proof.universe());
        state.addFinding("WORKLIST", whileStmt.getBegin().map(p -> p.line).orElse(null),
                whileStmt.getEnd().map(p -> p.line).orElse(null),
                proof.evidence(), "HIGH", proof.evidence());
        try {
            ComplexityExpr body = whileBodyCost(whileStmt, state, ipc, currentMethod, depth);
            if (body instanceof ComplexityExpr.Unknown) {
                return body;
            }
            return new ComplexityExpr.Product(List.of(proof.universe(), body));
        } finally {
            state.structuralCardinality.remove(proof.containerName());
        }
    }

    private ComplexityExpr whileBodyCost(
            WhileStmt whileStmt,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            int depth) {
        return whileStmt.getBody() instanceof BlockStmt block
                ? analyzeBlock(block, state, ipc, currentMethod, depth + 1)
                : analyzeStatement(whileStmt.getBody(), state, ipc, currentMethod, depth + 1);
    }

    private WorklistStructuralProof.Lookup worklistLookup(AnalysisState state) {
        return new WorklistStructuralProof.Lookup() {
            @Override
            public Optional<ComplexityExpr> sizeOf(Expression expression) {
                return mapSizeExpression(expression, state);
            }

            @Override
            public Optional<String> concreteType(String localName) {
                return Optional.ofNullable(state.localJdkConcreteTypes.get(localName));
            }
        };
    }

    private static void bindCompileTimeTable(String localName, Expression initializer, AnalysisState state) {
        Expression table = initializer;
        if (table instanceof ArrayCreationExpr array && array.getInitializer().isPresent()) {
            table = array.getInitializer().get();
        }
        if (table instanceof ArrayInitializerExpr) {
            state.sizeResolver.bindLocalAlias(localName, ComplexityExpr.one());
        }
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
                    if (init instanceof ObjectCreationExpr creation) {
                        state.registerLocalFromCreation(variable.getNameAsString(), creation);
                        state.noteProvenPriorityQueueComparator(variable.getNameAsString(), creation);
                    }
                    registerLocalSizeAlias(variable.getNameAsString(), init, state);
                    bindCompileTimeTable(variable.getNameAsString(), init, state);
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
        if (expression instanceof ArrayInitializerExpr initializer) {
            ComplexityExpr total = ComplexityExpr.one();
            for (Expression value : initializer.getValues()) {
                total = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(
                        total, analyzeExpression(value, state, ipc, currentMethod))));
            }
            return total;
        }
        if (expression instanceof ArrayCreationExpr arrayCreation && !arrayCreation.getLevels().isEmpty()) {
            ComplexityExpr magnitude = arrayAllocationMagnitude(
                    arrayCreation, state, ipc, currentMethod);
            state.addFinding("ALLOCATION", arrayCreation.getBegin().map(p -> p.line).orElse(null),
                    arrayCreation.getEnd().map(p -> p.line).orElse(null),
                    ComplexityExprSimplifier.toExpressionString(magnitude), "MEDIUM", "Array allocation");
            return ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(magnitude, ComplexityExpr.one())));
        }
        if (expression instanceof LambdaExpr lambda) {
            if (AnalysisState.isShallowComparator(lambda)) {
                return ComplexityExpr.one();
            }
            return unsupportedExpression(expression, state);
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
        Optional<ComplexityExpr> jdkCost = tryAnalyzeJdkCall(call, state, methodName);
        if (jdkCost.isPresent()) {
            return jdkCost.get();
        }
        if (call.getScope().isPresent() && call.getScope().get() instanceof NameExpr nameExpr
                && JdkCallTargetResolver.isSourceDefinedSimpleName(state.compilationUnit, nameExpr.getNameAsString())) {
            state.markTimeIncomplete(StaticAnalysisReasonCode.USER_TYPE_SHADOWS_JDK);
        }
        String scopeLabel = call.getScope().map(Object::toString).orElse("unknown");
        state.markOpaque("Unresolved external call: " + scopeLabel + "." + methodName);
        state.addFinding("OPAQUE_CALL", call.getBegin().map(p -> p.line).orElse(null),
                call.getEnd().map(p -> p.line).orElse(null), null, "LOW",
                "Unresolved call " + scopeLabel + "." + methodName + " — not treated as O(1)");
        state.markTimeIncomplete(StaticAnalysisReasonCode.OPAQUE_CALL);
        return new ComplexityExpr.Unknown("opaque call");
    }

    private record JdkBoundCall(JdkKnowledgeEntry entry, ComplexityExpr time, ComplexityExpr allocation) {
    }

    private Optional<ComplexityExpr> tryAnalyzeJdkCall(
            MethodCallExpr call,
            AnalysisState state,
            String methodName) {
        Optional<JdkBoundCall> bound = tryBindJdkCall(call, state, methodName);
        if (bound.isEmpty()) {
            return Optional.empty();
        }
        JdkBoundCall jdk = bound.get();
        if (!(jdk.time() instanceof ComplexityExpr.Unknown)) {
            if (jdk.entry() != null && jdk.entry().requiresHashKeyProof() && !state.hashKeyCostProvenForCall(call)) {
                state.markTimeIncomplete(StaticAnalysisReasonCode.JDK_CALLBACK_COST_UNRESOLVED);
                return Optional.of(new ComplexityExpr.Unknown("jdk hash key cost unresolved"));
            }
            if (jdk.entry() != null) {
                state.noteBasis(jdk.entry().boundBasis());
                state.addFinding("JDK_CALL", call.getBegin().map(p -> p.line).orElse(null),
                        call.getEnd().map(p -> p.line).orElse(null),
                        ComplexityExprSimplifier.toExpressionString(jdk.time()), "MEDIUM",
                        jdk.entry().note());
            }
        }
        return Optional.of(jdk.time());
    }

    private Optional<JdkBoundCall> tryBindJdkCall(
            MethodCallExpr call,
            AnalysisState state,
            String methodName) {
        Optional<JdkCallTarget> target = JdkCallTargetResolver.resolveStaticCall(call, state.compilationUnit);
        if (target.isEmpty()) {
            target = JdkCallTargetResolver.resolveInstanceCall(
                    call,
                    state.paramTypes,
                    state.localNameToType,
                    state.localJdkConcreteTypes,
                    state.compilationUnit);
        }
        if (target.isEmpty()) {
            return Optional.empty();
        }
        JdkCallTarget resolvedTarget = target.get();
        List<String> inferredParams = JdkCallSignatureInference.inferParameterTypes(
                call, state.localNameToRawType, state.paramTypes, state.localJdkConcreteTypes);
        Optional<JdkKnowledgeEntry> entry = knowledgeBase.matchOperation(resolvedTarget, methodName, inferredParams);
        if (entry.isEmpty()) {
            state.markTimeIncomplete(inferredParams.stream().anyMatch("unknown"::equals)
                    ? StaticAnalysisReasonCode.JDK_OVERLOAD_UNSUPPORTED
                    : StaticAnalysisReasonCode.JDK_OPERATION_UNSUPPORTED);
            state.markOpaque("Unsupported JDK operation: " + resolvedTarget.qualifiedType() + "." + methodName);
            return Optional.of(new JdkBoundCall(null,
                    new ComplexityExpr.Unknown("unsupported jdk operation"), ComplexityExpr.one()));
        }
        JdkKnowledgeEntry jdkEntry = entry.get();
        if (jdkEntry.requiresComparatorProof() && !state.comparatorCostProvenForCall(call)) {
            state.markTimeIncomplete(StaticAnalysisReasonCode.JDK_CALLBACK_COST_UNRESOLVED);
            return Optional.of(new JdkBoundCall(jdkEntry,
                    new ComplexityExpr.Unknown("jdk comparator cost unresolved"), ComplexityExpr.one()));
        }
        JdkCallSiteBinder.BindResult bound = bindJdkCall(jdkEntry, call, resolvedTarget, state);
        if (!bound.success()
                && bound.failureKind() == JdkCallSiteBinder.FailureKind.CARDINALITY_UNRESOLVED
                && call.getScope().isPresent()
                && call.getScope().get() instanceof NameExpr receiver) {
            Optional<WorklistStructuralProof.Proof> known = provenWorklistFor(call, receiver.getNameAsString(), state);
            if (known.isPresent() && !state.structuralCardinality.containsKey(receiver.getNameAsString())) {
                state.structuralCardinality.put(receiver.getNameAsString(), known.get().universe());
                try {
                    bound = bindJdkCall(jdkEntry, call, resolvedTarget, state);
                } finally {
                    state.structuralCardinality.remove(receiver.getNameAsString());
                }
            }
        }
        if (!bound.success()) {
            StaticAnalysisReasonCode code = switch (bound.failureKind()) {
                case CARDINALITY_UNRESOLVED -> StaticAnalysisReasonCode.JDK_CARDINALITY_UNRESOLVED;
                case CALLBACK_UNRESOLVED -> StaticAnalysisReasonCode.JDK_CALLBACK_COST_UNRESOLVED;
                default -> StaticAnalysisReasonCode.JDK_CALLSITE_SUBSTITUTION_FAILED;
            };
            state.markTimeIncomplete(code);
            return Optional.of(new JdkBoundCall(jdkEntry,
                    new ComplexityExpr.Unknown("jdk call-site bind failed"), ComplexityExpr.one()));
        }
        return Optional.of(new JdkBoundCall(jdkEntry, bound.time(), bound.allocation()));
    }

    private JdkCallSiteBinder.BindResult bindJdkCall(
            JdkKnowledgeEntry jdkEntry,
            MethodCallExpr call,
            JdkCallTarget resolvedTarget,
            AnalysisState state) {
        return JdkCallSiteBinder.bind(
                jdkEntry,
                call,
                resolvedTarget,
                expr -> mapSizeExpression(expr, state),
                expr -> state.resolveJdkReceiverCardinality(expr),
                state.documentedSymbolKeys());
    }

    private Optional<WorklistStructuralProof.Proof> provenWorklistFor(
            MethodCallExpr call, String container, AnalysisState state) {
        Optional<com.github.javaparser.ast.body.MethodDeclaration> method =
                call.findAncestor(com.github.javaparser.ast.body.MethodDeclaration.class);
        if (method.isEmpty()) {
            return Optional.empty();
        }
        for (WhileStmt loop : method.get().findAll(WhileStmt.class)) {
            WorklistStructuralProof.Attempt attempt = WorklistStructuralProof.attempt(loop, worklistLookup(state));
            if (attempt.proof().isPresent() && container.equals(attempt.proof().get().containerName())) {
                return attempt.proof();
            }
        }
        return Optional.empty();
    }

    private static boolean isNonTrivialJdkAllocation(ComplexityExpr allocation) {
        ComplexityExpr simplified = ComplexityExprSimplifier.simplify(allocation);
        return !(simplified instanceof ComplexityExpr.Constant constant && constant.value() == 1);
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
            if (siblingState.hasRecurrenceSiblingIncompleteness()) {
                state.absorbRecurrenceSiblingIncompleteness(siblingState);
                direct = Optional.empty();
            }
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
                entryMethod.getBody().orElse(new BlockStmt()), state, ipc, entryIdentity, entryIdentity, false);

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
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            MethodIdentity entryIdentity,
            boolean disjointNestedScope) {
        ComplexityExpr scopeLive = ComplexityExpr.one();
        ComplexityExpr nestedDisjointPeak = ComplexityExpr.one();
        for (Statement statement : block.getStatements()) {
            if (statement instanceof ExpressionStmt exprStmt && exprStmt.getExpression() instanceof VariableDeclarationExpr varDecl) {
                for (var variable : varDecl.getVariables()) {
                    state.registerLocalType(variable.getNameAsString(), varDecl.getElementType().asString());
                    if (variable.getInitializer().isPresent()) {
                        Expression init = variable.getInitializer().get();
                        if (init instanceof ObjectCreationExpr creation) {
                            state.registerLocalFromCreation(variable.getNameAsString(), creation);
                        }
                        registerLocalSizeAlias(variable.getNameAsString(), init, state);
                        ComplexityExpr alloc = allocationSize(
                                init, state, ipc, currentMethod, entryIdentity, false);
                        scopeLive = ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(scopeLive, alloc)));
                    }
                }
                continue;
            }
            ComplexityExpr stmtPeak = analyzeStatementSpacePeak(
                    statement, state, ipc, currentMethod, entryIdentity, disjointNestedScope);
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
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            MethodIdentity entryIdentity,
            boolean parentScope) {
        return switch (statement) {
            case BlockStmt block -> analyzeBlockSpacePeak(block, state, ipc, currentMethod, entryIdentity, true);
            case ForStmt forStmt -> {
                ConservativeLoopBoundProof.BoundMapper mapper = boundMapper(state);
                ConservativeLoopBoundProof.ProofResult proof = ConservativeLoopBoundProof.proveForLoop(forStmt, mapper);
                ComplexityExpr body = forStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, entryIdentity, true)
                        : analyzeStatementSpacePeak(forStmt.getBody(), state, ipc, currentMethod, entryIdentity, parentScope);
                if (proof.failure().isPresent()) {
                    yield body;
                }
                ComplexityExpr bound = proof.iterationBound().orElse(ComplexityExpr.one());
                Optional<ComplexityExpr> retained = CollectionGrowthSpaceSupport.retainedGrowthFromProvenForLoop(
                        forStmt, bound, state.localNameToRawType);
                if (retained.isPresent()) {
                    yield ComplexityExprSimplifier.simplify(new ComplexityExpr.Sum(List.of(body, retained.get())));
                }
                yield body;
            }
            case ForEachStmt forEach -> {
                ComplexityExpr body = forEach.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, entryIdentity, true)
                        : analyzeStatementSpacePeak(forEach.getBody(), state, ipc, currentMethod, entryIdentity, parentScope);
                yield body;
            }
            case WhileStmt whileStmt -> {
                ComplexityExpr body = whileStmt.getBody() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, entryIdentity, true)
                        : analyzeStatementSpacePeak(whileStmt.getBody(), state, ipc, currentMethod, entryIdentity, parentScope);
                yield body;
            }
            case ReturnStmt returnStmt -> returnStmt.getExpression()
                    .map(expr -> spacePeakForReturnExpression(expr, state, ipc, currentMethod, entryIdentity))
                    .orElse(ComplexityExpr.one());
            case ExpressionStmt expressionStmt -> expressionStmt.getExpression() == null
                    ? ComplexityExpr.one()
                    : allocationSize(expressionStmt.getExpression(), state, ipc, currentMethod, entryIdentity, false);
            case IfStmt ifStmt -> {
                ComplexityExpr thenPeak = ifStmt.getThenStmt() instanceof BlockStmt b
                        ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, entryIdentity, true)
                        : analyzeStatementSpacePeak(ifStmt.getThenStmt(), state, ipc, currentMethod, entryIdentity, parentScope);
                ComplexityExpr elsePeak = ifStmt.getElseStmt()
                        .map(s -> s instanceof BlockStmt b
                                ? analyzeBlockSpacePeak(b, state, ipc, currentMethod, entryIdentity, true)
                                : analyzeStatementSpacePeak(s, state, ipc, currentMethod, entryIdentity, parentScope))
                        .orElse(ComplexityExpr.one());
                yield ComplexityExprSimplifier.branchWorstCase(thenPeak, elsePeak);
            }
            default -> {
                state.markSpaceIncomplete(StaticAnalysisReasonCode.UNSUPPORTED_STATEMENT);
                yield new ComplexityExpr.Unknown("unsupported statement for space");
            }
        };
    }

    private ComplexityExpr spacePeakForReturnExpression(
            Expression expression,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            MethodIdentity entryIdentity) {
        if (!currentMethod.equals(entryIdentity)) {
            return ComplexityExpr.one();
        }
        if (ReturnAllocationSupport.isPassThroughHelperReturn(expression)) {
            return allocationSize(expression, state, ipc, currentMethod, entryIdentity, true);
        }
        if (ReturnAllocationSupport.isDefiniteRequiredOutputExpression(expression)) {
            return ComplexityExpr.one();
        }
        ComplexityExpr returnedMagnitude = rawAllocationMagnitude(
                expression, state, ipc, currentMethod, entryIdentity);
        if (returnedMagnitude instanceof ComplexityExpr.Constant constant && constant.value() == 1) {
            return ComplexityExpr.one();
        }
        state.markSpaceIncomplete(StaticAnalysisReasonCode.OUTPUT_OWNERSHIP_UNRESOLVED);
        return allocationSize(expression, state, ipc, currentMethod, entryIdentity, false);
    }

    private ComplexityExpr allocationSize(
            Expression expression,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            MethodIdentity entryIdentity,
            boolean passThroughReturn) {
        if (expression instanceof ArrayCreationExpr array && !array.getLevels().isEmpty()) {
            return arrayAllocationMagnitude(array, state, ipc, currentMethod);
        }
        if (expression instanceof ObjectCreationExpr creation) {
            if (creation.getArguments().isEmpty() || creation.getArgument(0) instanceof LambdaExpr) {
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
                            allocationSize(variable.getInitializer().get(), state, ipc, currentMethod, entryIdentity, false))));
                }
            }
            return total;
        }
        if (expression instanceof AssignExpr assign) {
            return allocationSize(assign.getValue(), state, ipc, currentMethod, entryIdentity, false);
        }
        if (expression instanceof MethodCallExpr call) {
            Optional<ComplexityExpr> helperSpace = tryAnalyzeHelperCallSpace(
                    call, state, ipc, currentMethod, entryIdentity, passThroughReturn);
            if (helperSpace.isPresent()) {
                return helperSpace.get();
            }
            Optional<JdkBoundCall> jdk = tryBindJdkCall(call, state, call.getNameAsString());
            if (jdk.isPresent()) {
                return composeJdkAllocationForSpace(jdk.get().allocation(), state, passThroughReturn);
            }
        }
        return ComplexityExpr.one();
    }

    private ComplexityExpr composeJdkAllocationForSpace(
            ComplexityExpr allocation,
            AnalysisState state,
            boolean passThroughReturn) {
        if (passThroughReturn || !isNonTrivialJdkAllocation(allocation)) {
            return ComplexityExpr.one();
        }
        return ComplexityExprSimplifier.simplify(allocation);
    }

    private Optional<ComplexityExpr> tryAnalyzeHelperCallSpace(
            MethodCallExpr call,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            MethodIdentity entryIdentity,
            boolean passThroughReturn) {
        String methodName = call.getNameAsString();
        if (call.getScope().isPresent() || ipc.userMethods.candidatesInType(state.declaringTypeName, methodName).isEmpty()) {
            return Optional.empty();
        }
        UserMethodOverloadResolver.Resolution resolution = UserMethodOverloadResolver.resolveUnqualifiedCall(
                call,
                state.declaringTypeName,
                ipc.userMethods,
                state.localNameToType,
                state.paramTypes);
        if (resolution.outcome() != UserMethodOverloadResolver.ResolutionOutcome.RESOLVED) {
            return Optional.empty();
        }
        MethodIdentity target = resolution.identity();
        if (target.equals(entryIdentity)) {
            return Optional.empty();
        }
        MethodSpaceSummary summary = resolveHelperSpaceSummary(
                call, state, ipc, currentMethod, entryIdentity, target, resolution.declaration(), 0);
        if (summary.incomplete()) {
            state.markSpaceIncomplete(StaticAnalysisReasonCode.HELPER_SPACE_EFFECT_INCOMPLETE);
        }
        return Optional.of(summary.callerOwnedEffect(passThroughReturn));
    }

    private MethodSpaceSummary resolveHelperSpaceSummary(
            MethodCallExpr call,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity caller,
            MethodIdentity entryIdentity,
            MethodIdentity target,
            MethodDeclaration method,
            int depth) {
        String summaryKey = target.cacheKey() + "#spaceSummary";
        String pendingKey = summaryKey + "#pending";
        MethodSpaceSummary cached = ipc.helperSpaceSummaryMemo.get(summaryKey);
        if (cached != null) {
            return cached;
        }
        if (ipc.helperSpaceSummaryMemo.containsKey(pendingKey)) {
            state.markSpaceIncomplete(StaticAnalysisReasonCode.HELPER_SPACE_EFFECT_INCOMPLETE);
            return MethodSpaceSummary.empty().mergeIncomplete(true);
        }
        if (depth > MAX_HELPER_ANALYSIS_DEPTH) {
            state.markSpaceIncomplete(StaticAnalysisReasonCode.HELPER_SPACE_EFFECT_INCOMPLETE);
            return MethodSpaceSummary.empty().mergeIncomplete(true);
        }
        ipc.helperSpaceSummaryMemo.put(pendingKey, MethodSpaceSummary.empty());
        AnalysisState helperState = state.forkForHelper(method);
        ComplexityExpr auxiliaryPeak = analyzeBlockSpacePeak(
                method.getBody().orElse(new BlockStmt()),
                helperState,
                ipc,
                target,
                entryIdentity,
                false);
        ComplexityExpr returnedAllocation = maxReturnedAllocationMagnitude(method, helperState, ipc, target, entryIdentity);
        boolean incomplete = helperState.spaceCompleteness == AnalysisDimensionCompleteness.INCOMPLETE;
        MethodSpaceSummary summary = new MethodSpaceSummary(auxiliaryPeak, returnedAllocation, incomplete);
        ipc.helperSpaceSummaryMemo.remove(pendingKey);
        ipc.helperSpaceSummaryMemo.put(summaryKey, summary);
        return summary;
    }

    private ComplexityExpr maxReturnedAllocationMagnitude(
            MethodDeclaration method,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            MethodIdentity entryIdentity) {
        ComplexityExpr peak = ComplexityExpr.one();
        for (ReturnStmt returnStmt : method.findAll(ReturnStmt.class)) {
            if (returnStmt.getExpression().isEmpty()) {
                continue;
            }
            ComplexityExpr magnitude = rawAllocationMagnitude(
                    returnStmt.getExpression().get(), state, ipc, currentMethod, entryIdentity);
            peak = ComplexityExprSimplifier.branchWorstCase(peak, magnitude);
        }
        return peak;
    }

    private ComplexityExpr rawAllocationMagnitude(
            Expression expression,
            AnalysisState state,
            InterproceduralContext ipc,
            MethodIdentity currentMethod,
            MethodIdentity entryIdentity) {
        if (expression instanceof ArrayCreationExpr array && !array.getLevels().isEmpty()) {
            return arrayAllocationMagnitude(array, state, ipc, currentMethod);
        }
        if (expression instanceof MethodCallExpr call) {
            Optional<ComplexityExpr> helper = tryAnalyzeHelperCallSpace(
                    call, state, ipc, currentMethod, entryIdentity, true);
            if (helper.isPresent()) {
                return helper.get();
            }
            Optional<JdkBoundCall> jdk = tryBindJdkCall(call, state, call.getNameAsString());
            if (jdk.isPresent()) {
                return ComplexityExprSimplifier.simplify(jdk.get().allocation());
            }
        }
        if (expression instanceof NameExpr) {
            return ComplexityExpr.one();
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
        final Map<String, String> localNameToRawType = new HashMap<>();
        final Map<String, String> localJdkConcreteTypes = new HashMap<>();
        final Map<String, ComplexityExpr> structuralCardinality = new HashMap<>();
        final Set<String> provenComparatorLocals = new HashSet<>();
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
            localNameToRawType.put(localName, rawType.trim());
            localNameToType.put(localName, MethodIdentity.eraseType(rawType));
            resolveConcreteJdkType(rawType.trim()).ifPresent(fqn -> localJdkConcreteTypes.put(localName, fqn));
        }

        void registerLocalFromCreation(String localName, ObjectCreationExpr creation) {
            String raw = creation.getType().asString();
            registerLocalType(localName, raw);
        }

        boolean hashKeyCostProvenForCall(MethodCallExpr call) {
            if (call.getArguments().isEmpty()) {
                return true;
            }
            Expression keyArg = call.getArgument(0);
            return isProvenSafeHashKeyExpression(keyArg);
        }

        private boolean isProvenSafeHashKeyExpression(Expression expression) {
            if (expression instanceof IntegerLiteralExpr || expression instanceof StringLiteralExpr) {
                return true;
            }
            if (expression instanceof NameExpr name) {
                String raw = localNameToRawType.getOrDefault(name.getNameAsString(),
                        paramTypes.get(name.getNameAsString()));
                if (raw == null) {
                    return false;
                }
                String simple = raw.contains(".") ? raw.substring(raw.lastIndexOf('.') + 1) : raw;
                if (JdkCallTargetResolver.isSourceDefinedSimpleName(compilationUnit, simple)) {
                    return false;
                }
                return isProvenSafeHashKeyType(raw);
            }
            Optional<String> inferred = JdkCallSignatureInference.inferExpressionType(
                    expression, localNameToRawType, paramTypes, localJdkConcreteTypes);
            if (inferred.isEmpty()) {
                return false;
            }
            String type = inferred.get();
            if (JdkCallTargetResolver.isSourceDefinedSimpleName(compilationUnit, type)) {
                return false;
            }
            return isProvenSafeHashKeyType(type);
        }

        private static boolean isProvenSafeHashKeyType(String rawType) {
            String base = rawType;
            int generic = rawType.indexOf('<');
            if (generic >= 0) {
                base = rawType.substring(0, generic).trim();
            }
            if (base.endsWith("[]")) {
                return false;
            }
            return switch (base) {
                case "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte", "char", "Character",
                        "String", "boolean", "Boolean", "double", "Double", "float", "Float" -> true;
                default -> base.startsWith("java.lang.")
                        && !"java.lang.Object".equals(base);
            };
        }

        void noteProvenPriorityQueueComparator(String localName, ObjectCreationExpr creation) {
            if (!creation.getType().getNameAsString().endsWith("PriorityQueue")) {
                return;
            }
            for (Expression argument : creation.getArguments()) {
                if (argument instanceof LambdaExpr lambda && isShallowComparator(lambda)) {
                    provenComparatorLocals.add(localName);
                }
            }
        }

        private static boolean isShallowComparator(LambdaExpr lambda) {
            Optional<Expression> body = lambda.getExpressionBody();
            if (body.isEmpty() && lambda.getBody() instanceof BlockStmt block
                    && block.getStatements().size() == 1
                    && block.getStatement(0) instanceof ReturnStmt ret) {
                body = ret.getExpression();
            }
            if (body.isEmpty()) {
                return false;
            }
            return isConstantTimeComparison(body.get(), lambda);
        }

        private static boolean isConstantTimeComparison(Expression expression, LambdaExpr lambda) {
            if (expression instanceof EnclosedExpr enclosed) {
                return isConstantTimeComparison(enclosed.getInner(), lambda);
            }
            if (expression instanceof MethodCallExpr call) {
                if (!call.getArguments().stream().allMatch(JavaStaticAnalyzer.AnalysisState::isShallowValue)) {
                    return false;
                }
                if ("compare".equals(call.getNameAsString()) && call.getScope().isPresent()
                        && isFixedWidthCompareOwner(call.getScope().get())) {
                    return true;
                }
                if ("compareTo".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                    return fixedWidthType(call.getScope().get(), lambda);
                }
                return false;
            }
            if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.MINUS) {
                return fixedWidthType(binary.getLeft(), lambda) && fixedWidthType(binary.getRight(), lambda);
            }
            return false;
        }

        private static boolean isShallowValue(Expression expression) {
            if (expression instanceof EnclosedExpr enclosed) {
                return isShallowValue(enclosed.getInner());
            }
            if (expression instanceof FieldAccessExpr access) {
                return isShallowValue(access.getScope());
            }
            return expression instanceof NameExpr || expression instanceof LiteralExpr;
        }

        private static boolean isFixedWidthCompareOwner(Expression scope) {
            if (!(scope instanceof NameExpr name)) {
                return false;
            }
            String simple = name.getNameAsString();
            int dot = simple.lastIndexOf('.');
            if (dot >= 0) {
                simple = simple.substring(dot + 1);
            }
            return switch (simple) {
                case "Integer", "Long", "Double", "Float", "Short", "Byte", "Character", "Boolean" -> true;
                default -> false;
            };
        }

        private static boolean fixedWidthType(Expression expression, LambdaExpr lambda) {
            if (expression instanceof EnclosedExpr enclosed) {
                return fixedWidthType(enclosed.getInner(), lambda);
            }
            if (expression instanceof LiteralExpr) {
                return expression instanceof IntegerLiteralExpr || expression instanceof LongLiteralExpr
                        || expression instanceof DoubleLiteralExpr || expression instanceof CharLiteralExpr;
            }
            if (!(expression instanceof NameExpr name)) {
                return false;
            }
            for (Parameter parameter : lambda.getParameters()) {
                if (!parameter.getNameAsString().equals(name.getNameAsString()) || parameter.getType().isUnknownType()) {
                    continue;
                }
                String erased = MethodIdentity.eraseType(parameter.getType().asString());
                return switch (erased) {
                    case "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte", "char", "Character",
                            "double", "Double", "float", "Float", "boolean", "Boolean" -> true;
                    default -> false;
                };
            }
            return false;
        }

        boolean comparatorCostProvenForCall(MethodCallExpr call) {
            if (call.getScope().isEmpty() || !(call.getScope().get() instanceof NameExpr name)) {
                return false;
            }
            if (provenComparatorLocals.contains(name.getNameAsString())) {
                return true;
            }
            String raw = localNameToRawType.getOrDefault(name.getNameAsString(),
                    paramTypes.get(name.getNameAsString()));
            if (raw == null) {
                return false;
            }
            return hasProvenComparableElement(raw);
        }

        private static boolean hasProvenComparableElement(String rawType) {
            int genericStart = rawType.indexOf('<');
            if (genericStart < 0) {
                return true;
            }
            int genericEnd = rawType.lastIndexOf('>');
            if (genericEnd <= genericStart) {
                return false;
            }
            String typeArg = rawType.substring(genericStart + 1, genericEnd).trim();
            if (typeArg.contains(",")) {
                return false;
            }
            return switch (typeArg) {
                case "Integer", "Long", "Double", "Float", "Short", "Byte", "Character", "int", "long",
                        "double", "float", "short", "byte", "char" -> true;
                default -> false;
            };
        }

        Optional<ComplexityExpr> resolveJdkReceiverCardinality(Expression expression) {
            if (expression instanceof NameExpr name) {
                ComplexityExpr structural = structuralCardinality.get(name.getNameAsString());
                if (structural != null) {
                    return Optional.of(structural);
                }
                String param = name.getNameAsString();
                for (ParameterShapeRegistry.ParameterShape shape : parameterShapes) {
                    if (shape.paramName().equals(param)
                            && shape.kind() == ParameterShapeRegistry.ParameterShape.Kind.COLLECTION) {
                        return sizeResolver.resolveSize(expression);
                    }
                }
                if (localJdkConcreteTypes.containsKey(param)) {
                    return Optional.empty();
                }
                if (paramTypes.containsKey(param) && isConcreteJdkTypeName(paramTypes.get(param))) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        private static boolean isConcreteJdkTypeName(String type) {
            if (type == null) {
                return false;
            }
            String lower = type.toLowerCase(Locale.ROOT);
            return lower.contains("priorityqueue") || lower.contains("arraylist") || lower.contains("treemap")
                    || lower.contains("hashmap") || lower.contains("arraydeque") || lower.contains("linkedlist");
        }

        private Optional<String> resolveConcreteJdkType(String rawType) {
            String base = rawType;
            if (base.contains("<")) {
                base = base.substring(0, base.indexOf('<'));
            }
            if (base.contains(".")) {
                return base.startsWith("java.") ? Optional.of(base) : Optional.empty();
            }
            return switch (base) {
                case "PriorityQueue" -> Optional.of("java.util.PriorityQueue");
                case "ArrayList" -> Optional.of("java.util.ArrayList");
                case "LinkedList" -> Optional.of("java.util.LinkedList");
                case "ArrayDeque" -> Optional.of("java.util.ArrayDeque");
                case "HashMap" -> Optional.of("java.util.HashMap");
                case "HashSet" -> Optional.of("java.util.HashSet");
                case "TreeMap" -> Optional.of("java.util.TreeMap");
                case "TreeSet" -> Optional.of("java.util.TreeSet");
                case "StringBuilder" -> Optional.of("java.lang.StringBuilder");
                default -> Optional.empty();
            };
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
            fork.localNameToRawType.putAll(localNameToRawType);
            fork.localJdkConcreteTypes.putAll(localJdkConcreteTypes);
            return fork;
        }

        boolean hasRecurrenceSiblingIncompleteness() {
            return timeCompleteness == AnalysisDimensionCompleteness.INCOMPLETE
                    || hasTimeBlockingReasonCodes()
                    || hasOpaqueDependency()
                    || hasUnknownLoop();
        }

        void absorbRecurrenceSiblingIncompleteness(AnalysisState sibling) {
            for (StaticAnalysisReasonCode code : sibling.reasonCodes) {
                if (code.blocksTimeAuthoritativeness()) {
                    markTimeIncomplete(code);
                } else {
                    markSpaceIncomplete(code);
                }
            }
            if (sibling.hasOpaqueDependency()) {
                markOpaque("Recurrence sibling work incomplete");
            }
            if (sibling.hasUnknownLoop()) {
                markUnknownLoop(StaticAnalysisReasonCode.UNKNOWN_LOOP_BOUND);
            }
            if (sibling.timeCompleteness == AnalysisDimensionCompleteness.INCOMPLETE && reasonCodes.isEmpty()) {
                markTimeIncomplete(StaticAnalysisReasonCode.INCOMPLETE_TIME_ANALYSIS);
            }
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
            worstBasis = ComplexityBoundBasisMerge.merge(worstBasis, basis);
        }

        boolean hasTimeBlockingReasonCodes() {
            return reasonCodes.stream().anyMatch(StaticAnalysisReasonCode::blocksTimeAuthoritativeness);
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
