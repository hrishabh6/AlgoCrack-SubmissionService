package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.interprocedural;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Directed call graph over {@link MethodIdentity} with SCC classification (Batch 2).
 */
public final class MethodCallGraph {

    public enum RecursionKind {
        NON_RECURSIVE,
        DIRECT_SELF_RECURSION,
        RECURSIVE_SCC
    }

    private final Map<MethodIdentity, Set<MethodIdentity>> edges = new HashMap<>();

    public void addEdge(MethodIdentity caller, MethodIdentity callee) {
        edges.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
    }

    public Set<MethodIdentity> callees(MethodIdentity caller) {
        return edges.getOrDefault(caller, Set.of());
    }

    public RecursionKind classify(MethodIdentity method) {
        Set<MethodIdentity> component = stronglyConnectedComponentContaining(method);
        if (component.size() == 1 && !edges.getOrDefault(method, Set.of()).contains(method)) {
            return RecursionKind.NON_RECURSIVE;
        }
        if (component.size() == 1 && edges.getOrDefault(method, Set.of()).contains(method)) {
            return RecursionKind.DIRECT_SELF_RECURSION;
        }
        return RecursionKind.RECURSIVE_SCC;
    }

    private Set<MethodIdentity> stronglyConnectedComponentContaining(MethodIdentity start) {
        Map<MethodIdentity, Integer> index = new HashMap<>();
        Map<MethodIdentity, Integer> lowlink = new HashMap<>();
        Deque<MethodIdentity> stack = new ArrayDeque<>();
        Set<MethodIdentity> onStack = new HashSet<>();
        int[] counter = {0};
        List<Set<MethodIdentity>> components = new ArrayList<>();

        class Tarjan {
            void visit(MethodIdentity v) {
                index.put(v, counter[0]);
                lowlink.put(v, counter[0]);
                counter[0]++;
                stack.push(v);
                onStack.add(v);
                for (MethodIdentity w : edges.getOrDefault(v, Set.of())) {
                    if (!index.containsKey(w)) {
                        visit(w);
                        lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
                    } else if (onStack.contains(w)) {
                        lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
                    }
                }
                if (lowlink.get(v).equals(index.get(v))) {
                    Set<MethodIdentity> component = new HashSet<>();
                    MethodIdentity w;
                    do {
                        w = stack.pop();
                        onStack.remove(w);
                        component.add(w);
                    } while (!w.equals(v));
                    components.add(component);
                }
            }
        }
        Tarjan tarjan = new Tarjan();
        for (MethodIdentity node : allNodes()) {
            if (!index.containsKey(node)) {
                tarjan.visit(node);
            }
        }
        for (Set<MethodIdentity> component : components) {
            if (component.contains(start)) {
                return component;
            }
        }
        return Set.of(start);
    }

    private Set<MethodIdentity> allNodes() {
        Set<MethodIdentity> nodes = new HashSet<>(edges.keySet());
        for (Set<MethodIdentity> targets : edges.values()) {
            nodes.addAll(targets);
        }
        return nodes;
    }
}
