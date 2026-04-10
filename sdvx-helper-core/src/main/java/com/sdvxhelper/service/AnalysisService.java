package com.sdvxhelper.service;

import com.sdvxhelper.model.MusicInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Provides Volforce breakdown analysis used by the analysis / tweet-generation feature.
 *
 * <p>Replaces the Python {@code SDVXLogger.analyze()} method.  Computes how many
 * charts can be improved and by how much, to project future Volforce values.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /**
     * Constructs the analysis service.
     */
    public AnalysisService() {
    }

    /**
     * Analyses the top-N best charts and identifies the minimum VF improvement
     * required to reach the given target total Volforce.
     *
     * @param top50        top-50 charts sorted by VF descending
     * @param currentVfInt current total VF integer (× 1000)
     * @param targetVfStr  target VF string (e.g. {@code "20.000"})
     * @return a human-readable analysis summary string
     */
    public String analyzeTargetVf(List<MusicInfo> top50, int currentVfInt, String targetVfStr) {
        double current = currentVfInt / 1000.0;
        double target;
        try {
            target = Double.parseDouble(targetVfStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid target VF string '{}'; defaulting to 20.000", targetVfStr);
            target = 20.0;
        }

        if (current >= target) {
            return String.format("Target VF %.3f already reached! Current: %.3f", target, current);
        }

        double gap = target - current;
        return String.format("Current VF: %.3f | Target: %.3f | Gap: %.3f | Charts in top-50: %d",
                current, target, gap, top50.size());
    }

    /**
     * Finds the chart in the top-50 with the highest potential VF gain if upgraded
     * to S rank with PUC lamp.
     *
     * @param top50 top-50 best charts
     * @return the chart with the most room for improvement, or {@code null} if empty
     */
    public MusicInfo findHighestPotentialChart(List<MusicInfo> top50) {
        MusicInfo best = null;
        int bestGain = 0;
        for (MusicInfo m : top50) {
            int lvInt = m.getLvAsInt();
            if (lvInt <= 0) continue;
            int maxVf = VolforceCalculator.computeSingleVf(9_900_000, "puc", lvInt);
            int gain = maxVf - m.getVf();
            if (gain > bestGain) {
                bestGain = gain;
                best = m;
            }
        }
        return best;
    }
}
