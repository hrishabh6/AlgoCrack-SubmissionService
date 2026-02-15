package com.hrishabh.algocracksubmissionservice.judging;

import com.hrishabh.algocracksubmissionservice.judging.comparator.ComparisonResult;
import com.hrishabh.algocracksubmissionservice.judging.comparator.OutputComparator;
import com.hrishabh.algocracksubmissionservice.judging.extractor.OutputExtractor;
import com.hrishabh.algocracksubmissionservice.judging.normalizer.OutputNormalizer;
import com.hrishabh.algocracksubmissionservice.judging.validator.OutputValidator;
import com.hrishabh.algocracksubmissionservice.judging.validator.ValidationResult;
import com.hrishabh.algocracksubmissionservice.judging.validator.ValidationStage;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * Orchestrates the 4-phase judging pipeline for a single testcase.
 *
 * Pipeline flow:
 * 1. Oracle guard (never penalize user for oracle failure)
 * 2. Extract (raw output → structured form)
 * 3. PRE_COMPARE validators (structural constraints that must hold regardless)
 * 4. Normalize (structured → canonical form)
 * 5. Compare (user canonical vs oracle canonical)
 * 6. POST_COMPARE validators (constraints verified after equality confirmed)
 */
@Slf4j
@Builder
public class JudgingPipeline {

    private final OutputExtractor extractor;
    private final OutputNormalizer normalizer;
    private final OutputComparator comparator;

    @Builder.Default
    private final List<OutputValidator> validators = Collections.emptyList();

    /**
     * Judge a single testcase's user output against oracle output.
     */
    public JudgingResult judge(ExecutionOutput userOutput, ExecutionOutput oracleOutput,
            JudgingContext context) {

        log.debug("[JudgingPipeline] Judging testcase for question {}", context.getQuestionId());

        // Guard: Oracle failure should never penalize the user
        if (oracleOutput.hasError()) {
            log.error("[JudgingPipeline] Oracle failure: {}", oracleOutput.getError());
            return JudgingResult.judgeError(
                    "Oracle execution failed: " + oracleOutput.getError());
        }

        // Phase 1: Extract
        Object userExtracted = extractor.extract(userOutput.getRawOutput(), context);
        Object oracleExtracted = extractor.extract(oracleOutput.getRawOutput(), context);

        log.debug("[JudgingPipeline] Extracted — user: {}, oracle: {}", userExtracted, oracleExtracted);

        // Phase 2: PRE_COMPARE validators
        for (OutputValidator validator : validators) {
            if (validator.getStage() == ValidationStage.PRE_COMPARE) {
                ValidationResult validation = validator.validate(userExtracted, oracleExtracted, context);
                if (!validation.isPassed()) {
                    log.debug("[JudgingPipeline] PRE_COMPARE validation failed: {}", validation.getReason());
                    return JudgingResult.failed(validation.getReason(),
                            safeToString(userExtracted), safeToString(oracleExtracted));
                }
            }
        }

        // Phase 3: Normalize
        Object userNormalized = normalizer.normalize(userExtracted, context);
        Object oracleNormalized = normalizer.normalize(oracleExtracted, context);

        log.debug("[JudgingPipeline] Normalized — user: {}, oracle: {}", userNormalized, oracleNormalized);

        // Phase 4: Compare
        ComparisonResult comparison = comparator.compare(userNormalized, oracleNormalized, context);
        if (!comparison.isPassed()) {
            log.debug("[JudgingPipeline] Comparison failed: {}", comparison.getReason());
            return JudgingResult.failed(comparison.getReason(),
                    safeToString(userNormalized), safeToString(oracleNormalized));
        }

        // Phase 5: POST_COMPARE validators
        for (OutputValidator validator : validators) {
            if (validator.getStage() == ValidationStage.POST_COMPARE) {
                ValidationResult validation = validator.validate(userNormalized, oracleNormalized, context);
                if (!validation.isPassed()) {
                    log.debug("[JudgingPipeline] POST_COMPARE validation failed: {}", validation.getReason());
                    return JudgingResult.failed(validation.getReason(),
                            safeToString(userNormalized), safeToString(oracleNormalized));
                }
            }
        }

        log.debug("[JudgingPipeline] All phases passed");
        return JudgingResult.passed(safeToString(userNormalized), safeToString(oracleNormalized));
    }

    private String safeToString(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}
