package com.hrishabh.algocracksubmissionservice.complexity.semantic;

import com.hrishabh.algocracksubmissionservice.client.ProblemServiceClient;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.Binding;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.Constraints;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.Entry;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.MutatedInput;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.Operation;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.OperationParameter;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.SizeVariable;
import com.hrishabh.algocracksubmissionservice.complexity.semantic.SemanticProfileView.Structure;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.JavaStaticAnalyzer;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticAnalysisResult;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.StaticFindingDraft;
import com.hrishabh.algocracksubmissionservice.dto.QuestionMetadataApiDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticMetadataIntegrationTest {

    private final JavaStaticAnalyzer analyzer = new JavaStaticAnalyzer(new JdkKnowledgeBase());

    @Test
    void compatibleProfileIsUsableAndUncheckedIsNot() {
        SemanticProfileLookup compatible = SemanticProfileResponseInterpreter.interpret(200, """
                {"code":"SEMANTIC_PROFILE_ACTIVE","profileId":"spp-q1-v1","schemaVersion":"semantic-v1",
                "semanticVersion":"v1","profileHash":"abc","compatibility":"COMPATIBLE",
                "profile":{"schemaVersion":"semantic-v1","families":["MATRIX"],"entry":{"kind":"FUNCTION"},
                "sizeVariables":[],"structures":[],"mutatedInputs":[]}}
                """);
        assertTrue(compatible.usable());
        assertEquals("spp-q1-v1", compatible.profileId());

        SemanticProfileLookup unchecked = SemanticProfileResponseInterpreter.interpret(200, """
                {"code":"SEMANTIC_PROFILE_ACTIVE","compatibility":"UNCHECKED","profileId":"spp-q1-v1"}
                """);
        assertEquals(SemanticProfileAvailability.UNCHECKED, unchecked.availability());
        assertFalse(unchecked.usable());
    }

    @Test
    void missingStaleIncompatibleAndUnavailableStayOnTheGenericPath() {
        assertEquals("SEMANTIC_PROFILE_MISSING", gate(404, "{\"code\":\"SEMANTIC_PROFILE_MISSING\"}").limitation());
        assertEquals("SEMANTIC_PROFILE_STALE", gate(409, "{\"code\":\"SEMANTIC_PROFILE_STALE\",\"compatibility\":\"STALE\"}").limitation());
        assertEquals("SEMANTIC_PROFILE_INCOMPATIBLE",
                gate(409, "{\"code\":\"SEMANTIC_PROFILE_INCOMPATIBLE\",\"compatibility\":\"INCOMPATIBLE\"}").limitation());
        assertEquals("SEMANTIC_PROFILE_UNAVAILABLE",
                SemanticProfileResponseInterpreter.interpretTransportFailure().limitationCode());

        StaticAnalysisResult generic = analyzer.analyze(loopSource(), matrixMetadata());
        StaticAnalysisResult missing = gate(404, "{\"code\":\"SEMANTIC_PROFILE_MISSING\"}").result();
        assertEquals(generic.timeExpression().toString(), missing.timeExpression().toString());
        assertTrue(missing.limitations().contains("SEMANTIC_PROFILE_MISSING"));
        assertFalse(generic.limitations().contains("SEMANTIC_PROFILE_MISSING"));
    }

    @Test
    void problemServiceFailureDoesNotThrow() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(), any(), eq(SemanticProfileResponse.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        ProblemServiceClient client = new ProblemServiceClient(restTemplate, "http://problem", "");

        SemanticProfileLookup lookup = client.getSemanticProfile(1L);

        assertEquals(SemanticProfileAvailability.UNAVAILABLE, lookup.availability());
    }

    @Test
    void matrixRowsAndColumnsFollowTheSemanticBinding() {
        SemanticProfileView swapped = functionProfile(
                List.of(variable("m", "DIMENSION", 0, "ROWS"), variable("n", "DIMENSION", 0, "COLUMNS")),
                List.of(structure(0, "MATRIX", null)));
        StaticAnalysisResult result = analyzer.analyze(loopSource(), matrixMetadata(), lookup(swapped));
        assertTrue(result.timeExpression().toString().contains("m"));
        assertFalse(result.timeExpression().toString().contains("var(n)"));
    }

    @Test
    void listAndTreeNodeCountsUseSemanticSymbols() {
        SemanticShapeAdapter.Adapted list = SemanticShapeAdapter.adaptFunction(
                metadata("reverseKGroup", List.of("ListNode", "int"), List.of("head", "k")),
                null,
                functionProfile(
                        List.of(variable("n", "COUNT", 0, "NODE_COUNT"), variable("k", "COUNT", 1, "SCALAR")),
                        List.of(structure(0, "LIST_NODE", null))));
        assertEquals("n", list.shapes().get(0).primarySymbol());
        assertEquals("k", list.shapes().get(1).primarySymbol());

        SemanticShapeAdapter.Adapted tree = SemanticShapeAdapter.adaptFunction(
                metadata("flatten", List.of("TreeNode"), List.of("root")),
                null,
                functionProfile(List.of(variable("n", "COUNT", 0, "NODE_COUNT")), List.of(structure(0, "TREE_NODE", null))));
        assertEquals("n", tree.shapes().get(0).primarySymbol());
        assertTrue(tree.variables().containsKey("n"));
    }

    @Test
    void graphVertexAndEdgeVariablesAreExposed() {
        SemanticShapeAdapter.Adapted edges = SemanticShapeAdapter.adaptFunction(
                metadata("criticalConnections", List.of("int", "List<List<Integer>>"), List.of("n", "connections")),
                null,
                functionProfile(
                        List.of(variable("v", "COUNT", 0, "SCALAR"), variable("e", "COUNT", 1, "EDGE_COUNT")),
                        List.of(structure(1, "EDGE_LIST", null))));
        assertEquals("v", edges.shapes().get(0).primarySymbol());
        assertEquals("e", edges.shapes().get(1).primarySymbol());

        SemanticShapeAdapter.Adapted node = SemanticShapeAdapter.adaptFunction(
                metadata("cloneGraph", List.of("Node"), List.of("node")),
                null,
                functionProfile(
                        List.of(variable("v", "COUNT", 0, "NODE_COUNT"), variable("e", "COUNT", 0, "EDGE_COUNT")),
                        List.of(structure(0, "GRAPH_NODE", null))));
        assertEquals("v", node.shapes().get(0).primarySymbol());
        assertTrue(node.variables().containsKey("e"));
    }

    @Test
    void sudokuFixedShapeIsDocumentedWithoutASearchVariable() {
        SemanticProfileView sudoku = functionProfile(
                List.of(variable("r", "DIMENSION", 0, "ROWS"), variable("c", "DIMENSION", 0, "COLUMNS")),
                List.of(new Structure(new Binding(0, null, null, null), "CHAR_MATRIX", "CHAR", "JSON_NESTED_ARRAY",
                        new Constraints(true, null, null, null, null, 9, 9))));
        SemanticShapeAdapter.Adapted adapted = SemanticShapeAdapter.adaptFunction(
                metadata("solveSudoku", List.of("char[][]"), List.of("board")), null, sudoku);
        assertEquals("r", adapted.shapes().get(0).rowSymbol().orElseThrow());
        assertEquals("c", adapted.shapes().get(0).colSymbol().orElseThrow());
        assertTrue(adapted.variables().get("r").contains("fixed 9"));
        assertTrue(adapted.variables().get("c").contains("fixed 9"));
        assertTrue(adapted.variables().keySet().stream().noneMatch(name -> name.toLowerCase().contains("search")));
    }

    @Test
    void codecOperationsAreAnalyzedIndependently() {
        SemanticProfileView codec = new SemanticProfileView(
                "semantic-v1",
                List.of("BINARY_TREE", "DESIGN_CLASS", "MULTI_ENTRY"),
                new Entry("DESIGN_CLASS", "Codec", "OPERATION_TRACE", List.of(
                        new Operation("Codec", "CONSTRUCTOR", List.of(), null),
                        new Operation("serialize", "METHOD",
                                List.of(new OperationParameter("TREE_NODE", "LEVEL_ORDER")), "STRING"),
                        new Operation("deserialize", "METHOD",
                                List.of(new OperationParameter("STRING", null)), "TREE_NODE"))),
                List.of(
                        new SizeVariable("n", "COUNT", new Binding(null, "serialize", 0, "NODE_COUNT")),
                        new SizeVariable("s", "LENGTH", new Binding(null, "deserialize", 0, "LENGTH"))),
                List.of(),
                List.of());
        QuestionMetadataApiDto metadata = QuestionMetadataApiDto.builder()
                .functionName("Codec")
                .questionType("DESIGN_CLASS")
                .paramNames(List.of())
                .paramTypes(List.of())
                .build();
        StaticAnalysisResult without = analyzer.analyze(codecSource(), metadata);
        assertEquals("ENTRY_METHOD_UNRESOLVED", without.errorCode());

        StaticAnalysisResult with = analyzer.analyze(codecSource(), metadata, lookup(codec));
        assertTrue(with.limitations().contains("SEMANTIC_MULTI_ENTRY"));
        assertTrue(finding(with, "serialize"));
        assertTrue(finding(with, "deserialize"));
        assertTrue(with.findings().stream().anyMatch(item ->
                "deserialize".equals(item.expression()) && item.summary().contains("O(s)")));
        assertFalse(with.findings().stream().anyMatch(item -> "ignored".equals(item.expression())));
    }

    private Gated gate(int status, String body) {
        SemanticProfileLookup lookup = SemanticProfileResponseInterpreter.interpret(status, body);
        StaticAnalysisResult result = analyzer.analyze(loopSource(), matrixMetadata(), lookup.usable() ? lookup : null);
        if (lookup.limitationCode() != null) {
            result = result.withLimitation(lookup.limitationCode());
        }
        return new Gated(result, lookup.limitationCode());
    }

    private record Gated(StaticAnalysisResult result, String limitation) {
    }

    private static boolean finding(StaticAnalysisResult result, String operation) {
        return result.findings().stream().map(StaticFindingDraft::expression).anyMatch(operation::equals);
    }

    private static SemanticProfileLookup lookup(SemanticProfileView profile) {
        return new SemanticProfileLookup(
                SemanticProfileAvailability.COMPATIBLE, "spp", "semantic-v1", "v1", "hash", profile);
    }

    private static String loopSource() {
        return """
                public class Solution {
                  public int trap(int[][] heightMap) {
                    int s = 0;
                    for (int i = 0; i < heightMap.length; i++) s++;
                    return s;
                  }
                }
                """;
    }

    private static String codecSource() {
        return """
                public class Codec {
                  public int ignored() { return 1; }
                  public String serialize(TreeNode root) { return null; }
                  public TreeNode deserialize(String data) {
                    int s = 0;
                    for (int i = 0; i < data.length(); i++) s++;
                    return null;
                  }
                }
                """;
    }

    private static QuestionMetadataApiDto matrixMetadata() {
        return metadata("trapRainWater", List.of("int[][]"), List.of("heightMap"));
    }

    private static QuestionMetadataApiDto metadata(String function, List<String> types, List<String> names) {
        return QuestionMetadataApiDto.builder()
                .functionName(function)
                .paramTypes(types)
                .paramNames(names)
                .build();
    }

    private static SemanticProfileView functionProfile(List<SizeVariable> variables, List<Structure> structures) {
        return new SemanticProfileView(
                "semantic-v1", List.of("ARRAY"), new Entry("FUNCTION", null, null, null),
                variables, structures, List.of(new MutatedInput(0, "MATRIX")));
    }

    private static SizeVariable variable(String name, String kind, int index, String dimension) {
        return new SizeVariable(name, kind, new Binding(index, null, null, dimension));
    }

    private static Structure structure(int index, String kind, Constraints constraints) {
        return new Structure(new Binding(index, null, null, null), kind, null, null, constraints);
    }
}
