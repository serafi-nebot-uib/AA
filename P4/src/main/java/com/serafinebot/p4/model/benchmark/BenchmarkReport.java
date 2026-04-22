package com.serafinebot.p4.model.benchmark;

import java.util.List;

/**
 * Full benchmark result set.
 *
 * @param config benchmark configuration that produced the report
 * @param queuePoints aggregated points collected for priority-queue comparison
 * @param modePoints aggregated points collected for requested compression mode comparison
 */
public record BenchmarkReport(
    BenchmarkConfig config,
    List<QueueBenchmarkPoint> queuePoints,
    List<CompressionModeBenchmarkPoint> modePoints
) {
    public BenchmarkReport {
        queuePoints = List.copyOf(queuePoints);
        modePoints = List.copyOf(modePoints);
    }

    public BenchmarkReport(BenchmarkConfig config, List<QueueBenchmarkPoint> queuePoints) {
        this(config, queuePoints, List.of());
    }
}
