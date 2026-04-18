package com.serafinebot.p4.model.benchmark;

import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

/**
 * Progress snapshot for long-running benchmark execution.
 *
 * @param completedSteps completed measurement steps
 * @param totalSteps total measurement steps
 * @param profile current dataset profile
 * @param strategy current queue strategy
 * @param sizeBytes current input size in bytes
 */
public record BenchmarkProgressSnapshot(
    int completedSteps,
    int totalSteps,
    BenchmarkProfile profile,
    PriorityQueueStrategy strategy,
    int sizeBytes
) {
    public double completion() {
        if (totalSteps <= 0) {
            return 1.0;
        }
        return Math.min(1.0, completedSteps / (double) totalSteps);
    }
}
