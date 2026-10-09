package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Stable JDK operation key: owner + method + static/instance + erased parameter types.
 */
public record JdkOperationIdentity(
        String qualifiedOwner,
        String methodName,
        boolean staticMethod,
        List<String> parameterTypes) {

    public JdkOperationIdentity {
        Objects.requireNonNull(qualifiedOwner, "qualifiedOwner");
        Objects.requireNonNull(methodName, "methodName");
        parameterTypes = List.copyOf(parameterTypes);
    }

    public String patternKey() {
        return (qualifiedOwner + "#" + methodName + "#" + (staticMethod ? "static" : "instance") + "#"
                + String.join(",", parameterTypes)).toLowerCase(Locale.ROOT);
    }

    public static String eraseType(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String base = raw.trim();
        int generic = base.indexOf('<');
        if (generic >= 0) {
            base = base.substring(0, generic);
        }
        if (base.endsWith("[]")) {
            return base;
        }
        return base;
    }
}
