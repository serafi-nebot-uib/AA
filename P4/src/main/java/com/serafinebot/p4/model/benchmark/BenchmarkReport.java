package com.serafinebot.p4.model.benchmark;

import java.util.List;

/**
 * Full benchmark result set.
 *
 * @param config benchmark configuration that produced the report
 * @param points aggregated points collected during the run
 */
public record BenchmarkReport(
    BenchmarkConfig config,
    List<BenchmarkPoint> points
) {
    public BenchmarkReport {
        points = List.copyOf(points);
    }
}
