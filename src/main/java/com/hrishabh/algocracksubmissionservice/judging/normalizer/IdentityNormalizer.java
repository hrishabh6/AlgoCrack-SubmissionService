package com.hrishabh.algocracksubmissionservice.judging.normalizer;

import com.hrishabh.algocracksubmissionservice.judging.JudgingContext;
import org.springframework.stereotype.Component;

/**
 * Identity normalizer — no-op, pass-through.
 * Default normalizer when output order matters and no special normalization
 * needed.
 */
@Component
public class IdentityNormalizer implements OutputNormalizer {

    @Override
    public Object normalize(Object extracted, JudgingContext context) {
        return extracted;
    }
}
