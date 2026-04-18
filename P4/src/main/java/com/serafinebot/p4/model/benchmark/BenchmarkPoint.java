package com.serafinebot.p4.model.benchmark;

import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

/**
 * One aggregated benchmark measurement.
 *
 * @param profile generated dataset profile
 * @param strategy priority queue strategy used by the codec
 * @param sizeBytes generated file size in bytes
 * @param compressionMillis average compression time in milliseconds
 * @param decompressionMillis average decompression time in milliseconds
 * @param entropy average observed entropy in bits per symbol
 * @param averageCodeLength average observed Huffman code length in bits per symbol
 * @param compressionPercentage average compression gain in percent
 */
public record BenchmarkPoint(
    BenchmarkProfile profile,
    PriorityQueueStrategy strategy,
    int sizeBytes,
    double compressionMillis,
    double decompressionMillis,
    double entropy,
    double averageCodeLength,
    double compressionPercentage
) {
}
