package com.hrishabh.algocracksubmissionservice.judging.extractor;

import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import org.springframework.stereotype.Component;

/**
 * Identity extractor — pass-through, returns raw string as-is.
 * Default extractor for primitive return types (int, boolean, string).
 */
@Component
public class IdentityExtractor implements OutputExtractor {

    @Override
    public Object extract(String rawOutput, JudgingContext context) {
        return rawOutput;
    }
}
