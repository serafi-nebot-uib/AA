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
 * @param entropy average observed entropy in bits per symbol
 * @param averageCodeLength average observed Huffman code length in bits per symbol
 * @param compressionPercentage average compression gain in percent
 */
public record QueueBenchmarkPoint(
    String sourceName,
    PriorityQueueStrategy strategy,
    long sizeBytes,
    double compressionTreeBuildMillis,
    double decompressionTreeBuildMillis,
    double entropy,
    double averageCodeLength,
    double compressionPercentage
) {
}
