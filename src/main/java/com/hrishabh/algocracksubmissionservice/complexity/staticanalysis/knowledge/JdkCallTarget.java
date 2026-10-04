package com.hrishabh.algocracksubmissionservice.complexity.staticanalysis.knowledge;

import java.util.Optional;

public record JdkCallTarget(String qualifiedType, boolean staticCall) {

    public Optional<JdkCallTarget> normalized() {
        if (qualifiedType == null || qualifiedType.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new JdkCallTarget(qualifiedType, staticCall));
    }
}
