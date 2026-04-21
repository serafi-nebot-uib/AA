package com.serafinebot.p4.model.benchmark;

import java.util.List;

/**
 * Full benchmark result set.
 *
 * @param config benchmark configuration that produced the report
 * @param points aggregated points collected during the run
 * @param modePoints aggregated points collected for mode comparison
 */
public record BenchmarkReport(
    BenchmarkConfig config,
    List<BenchmarkPoint> points,
    List<BenchmarkModePoint> modePoints
) {
    public BenchmarkReport {
        points = List.copyOf(points);
        modePoints = List.copyOf(modePoints);
    }

    public BenchmarkReport(BenchmarkConfig config, List<BenchmarkPoint> points) {
        this(config, points, List.of());
    }
}
