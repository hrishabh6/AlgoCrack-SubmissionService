package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.expr.ComplexityExpr;
import com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.model.ComplexityBoundBasis;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkCallSiteBinder.roles;
import static com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase.logVar;
import static com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase.op;
import static com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge.JdkKnowledgeBase.tv;

final class JdkOperationCatalog {

    private JdkOperationCatalog() {
    }

    static List<JdkKnowledgeEntry> all() {
        List<JdkKnowledgeEntry> list = new ArrayList<>();
        arrays(list);
        collections(list);
        priorityQueue(list);
        arrayList(list);
        linkedList(list);
        arrayDeque(list);
        hashMap(list);
        hashSet(list);
        treeMap(list);
        string(list);
        stringBuilder(list);
        system(list);
        return List.copyOf(list);
    }

    private static void arrays(List<JdkKnowledgeEntry> list) {
        list.add(op("java.util.Arrays", "sort", true, List.of("int[]"), ComplexityBoundBasis.WORST_CASE,
                new ComplexityExpr.Product(List.of(tv(JdkTemplateVariable.ARG0_SIZE),
                        logVar(JdkTemplateVariable.ARG0_SIZE))),
                ComplexityExpr.one(), roles(JdkTemplateVariable.ARG0_SIZE), false, false, "Arrays.sort int[]"));
        list.add(op("java.util.Arrays", "sort", true, List.of("Object[]"), ComplexityBoundBasis.WORST_CASE,
                new ComplexityExpr.Product(List.of(tv(JdkTemplateVariable.ARG0_SIZE),
                        logVar(JdkTemplateVariable.ARG0_SIZE))),
                ComplexityExpr.one(), roles(JdkTemplateVariable.ARG0_SIZE), false, false, "Arrays.sort Object[]"));
        list.add(op("java.util.Arrays", "sort", true, List.of("int[]", "int", "int"), ComplexityBoundBasis.WORST_CASE,
                new ComplexityExpr.Product(List.of(tv(JdkTemplateVariable.RANGE_LENGTH),
                        logVar(JdkTemplateVariable.RANGE_LENGTH))),
                ComplexityExpr.one(), roles(JdkTemplateVariable.RANGE_LENGTH), false, false, "Arrays.sort range"));
        list.add(op("java.util.Arrays", "copyOf", true, List.of("int[]", "int"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.ARG1_SIZE), tv(JdkTemplateVariable.ARG1_SIZE),
                roles(JdkTemplateVariable.ARG1_SIZE), false, false, "Arrays.copyOf"));
        list.add(op("java.util.Arrays", "fill", true, List.of("int[]"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.ARG0_SIZE), ComplexityExpr.one(),
                roles(JdkTemplateVariable.ARG0_SIZE), false, false, "Arrays.fill"));
        list.add(op("java.util.Arrays", "binarySearch", true, List.of("int[]", "int"), ComplexityBoundBasis.WORST_CASE,
                logVar(JdkTemplateVariable.ARG0_SIZE), ComplexityExpr.one(),
                roles(JdkTemplateVariable.ARG0_SIZE), false, false, "Arrays.binarySearch"));
    }

    private static void collections(List<JdkKnowledgeEntry> list) {
        list.add(op("java.util.Collections", "sort", true, List.of("List"), ComplexityBoundBasis.WORST_CASE,
                new ComplexityExpr.Product(List.of(tv(JdkTemplateVariable.ARG0_SIZE),
                        logVar(JdkTemplateVariable.ARG0_SIZE))),
                ComplexityExpr.one(), roles(JdkTemplateVariable.ARG0_SIZE), false, false, "Collections.sort"));
    }

    private static void priorityQueue(List<JdkKnowledgeEntry> list) {
        EnumSet<JdkTemplateVariable> card = roles(JdkTemplateVariable.RECEIVER_CARDINALITY);
        list.add(op("java.util.PriorityQueue", "offer", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                logVar(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(), card, true, false, "PriorityQueue.offer"));
        list.add(op("java.util.PriorityQueue", "add", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                logVar(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(), card, true, false, "PriorityQueue.add"));
        list.add(op("java.util.PriorityQueue", "poll", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                logVar(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(), card, true, false, "PriorityQueue.poll"));
        list.add(op("java.util.PriorityQueue", "remove", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                logVar(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(), card, true, false, "PriorityQueue.remove()"));
        list.add(op("java.util.PriorityQueue", "remove", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(), card, false, false, "PriorityQueue.remove(Object)"));
        list.add(op("java.util.PriorityQueue", "peek", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "PriorityQueue.peek"));
        list.add(op("java.util.PriorityQueue", "element", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "PriorityQueue.element"));
        list.add(op("java.util.PriorityQueue", "size", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "PriorityQueue.size"));
        list.add(op("java.util.PriorityQueue", "isEmpty", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "PriorityQueue.isEmpty"));
        list.add(op("java.util.PriorityQueue", "contains", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(), card, false, false, "PriorityQueue.contains"));
    }

    private static void arrayList(List<JdkKnowledgeEntry> list) {
        list.add(op("java.util.ArrayList", "get", false, List.of("int"), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "ArrayList.get"));
        list.add(op("java.util.ArrayList", "set", false, List.of("int", "Object"), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "ArrayList.set"));
        list.add(op("java.util.ArrayList", "size", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "ArrayList.size"));
        list.add(op("java.util.ArrayList", "add", false, List.of("Object"), ComplexityBoundBasis.AMORTIZED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "ArrayList.add amortized"));
        list.add(op("java.util.ArrayList", "add", false, List.of("int", "Object"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(),
                roles(JdkTemplateVariable.RECEIVER_CARDINALITY), false, false, "ArrayList.add(index)"));
        list.add(op("java.util.ArrayList", "remove", false, List.of("int"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(),
                roles(JdkTemplateVariable.RECEIVER_CARDINALITY), false, false, "ArrayList.remove(index)"));
        list.add(op("java.util.ArrayList", "contains", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(),
                roles(JdkTemplateVariable.RECEIVER_CARDINALITY), false, false, "ArrayList.contains"));
    }

    private static void linkedList(List<JdkKnowledgeEntry> list) {
        list.add(op("java.util.LinkedList", "addFirst", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "LinkedList.addFirst"));
        list.add(op("java.util.LinkedList", "addLast", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "LinkedList.addLast"));
        list.add(op("java.util.LinkedList", "get", false, List.of("int"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(),
                roles(JdkTemplateVariable.RECEIVER_CARDINALITY), false, false, "LinkedList.get indexed"));
    }

    private static void arrayDeque(List<JdkKnowledgeEntry> list) {
        list.add(op("java.util.ArrayDeque", "offer", false, List.of("Object"), ComplexityBoundBasis.AMORTIZED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "ArrayDeque.offer"));
        list.add(op("java.util.ArrayDeque", "poll", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "ArrayDeque.poll"));
        list.add(op("java.util.ArrayDeque", "contains", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(),
                roles(JdkTemplateVariable.RECEIVER_CARDINALITY), false, false, "ArrayDeque.contains"));
    }

    private static void hashMap(List<JdkKnowledgeEntry> list) {
        list.add(op("java.util.HashMap", "get", false, List.of("Object"), ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, true, "HashMap.get"));
        list.add(op("java.util.HashMap", "put", false, List.of("Object", "Object"), ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, true, "HashMap.put"));
        list.add(op("java.util.HashMap", "containsKey", false, List.of("Object"), ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, true, "HashMap.containsKey"));
        list.add(op("java.util.HashMap", "putIfAbsent", false, List.of("Object", "Object"), ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, true, "HashMap.putIfAbsent"));
        list.add(op("java.util.HashMap", "remove", false, List.of("Object"), ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, true, "HashMap.remove"));
    }

    private static void hashSet(List<JdkKnowledgeEntry> list) {
        list.add(op("java.util.HashSet", "contains", false, List.of("Object"), ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, true, "HashSet.contains"));
        list.add(op("java.util.HashSet", "add", false, List.of("Object"), ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, true, "HashSet.add"));
        list.add(op("java.util.HashSet", "remove", false, List.of("Object"), ComplexityBoundBasis.EXPECTED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, true, "HashSet.remove"));
    }

    private static void treeMap(List<JdkKnowledgeEntry> list) {
        EnumSet<JdkTemplateVariable> card = roles(JdkTemplateVariable.RECEIVER_CARDINALITY);
        list.add(op("java.util.TreeMap", "get", false, List.of("Object"), ComplexityBoundBasis.WORST_CASE,
                logVar(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(), card, true, false, "TreeMap.get"));
        list.add(op("java.util.TreeMap", "put", false, List.of("Object", "Object"), ComplexityBoundBasis.WORST_CASE,
                logVar(JdkTemplateVariable.RECEIVER_CARDINALITY), ComplexityExpr.one(), card, true, false, "TreeMap.put"));
    }

    private static void string(List<JdkKnowledgeEntry> list) {
        EnumSet<JdkTemplateVariable> rcv = roles(JdkTemplateVariable.RECEIVER_SIZE);
        list.add(op("java.lang.String", "length", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "String.length"));
        list.add(op("java.lang.String", "charAt", false, List.of("int"), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "String.charAt"));
        list.add(op("java.lang.String", "toCharArray", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.RECEIVER_SIZE), tv(JdkTemplateVariable.RECEIVER_SIZE), rcv, false, false, "String.toCharArray"));
    }

    private static void stringBuilder(List<JdkKnowledgeEntry> list) {
        list.add(op("java.lang.StringBuilder", "length", false, List.of(), ComplexityBoundBasis.WORST_CASE,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "StringBuilder.length"));
        list.add(op("java.lang.StringBuilder", "append", false, List.of("String"), ComplexityBoundBasis.AMORTIZED_ASSUMPTION,
                ComplexityExpr.one(), ComplexityExpr.one(), EnumSet.noneOf(JdkTemplateVariable.class), false, false, "StringBuilder.append"));
    }

    private static void system(List<JdkKnowledgeEntry> list) {
        list.add(op("java.lang.System", "arraycopy", true,
                List.of("Object", "int", "Object", "int", "int"), ComplexityBoundBasis.WORST_CASE,
                tv(JdkTemplateVariable.ARG4_SIZE), ComplexityExpr.one(),
                roles(JdkTemplateVariable.ARG4_SIZE), false, false, "System.arraycopy"));
    }
}
