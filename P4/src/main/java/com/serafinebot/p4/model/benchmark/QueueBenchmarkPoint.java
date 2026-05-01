package com.serafinebot.p4.model.benchmark;

import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

/**
 * One aggregated benchmark measurement.
 *
 * @param sourceName corpus file name used for the measurement
 * @param strategy priority queue strategy used by the codec
 * @param sizeBytes input file size in bytes
 * @param compressionTreeBuildMillis average tree-build time during compression in milliseconds
 * @param decompressionTreeBuildMillis average tree-build time during decompression in milliseconds
 */
public record QueueBenchmarkPoint(
    String sourceName,
    PriorityQueueStrategy strategy,
    long sizeBytes,
    double compressionTreeBuildMillis,
    double decompressionTreeBuildMillis
) {
}
