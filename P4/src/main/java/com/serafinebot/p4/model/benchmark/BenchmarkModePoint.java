package com.serafinebot.p4.model.benchmark;

import com.serafinebot.p4.model.archive.CompressionMode;

/**
 * One benchmark measurement focused on compression mode comparison.
 *
 * @param sourceName corpus file name used for the measurement
 * @param mode forced compression mode used for the run
 * @param sizeBytes input file size in bytes
 * @param compressionPercentage average compression gain in percent
 */
public record BenchmarkModePoint(
    String sourceName,
    CompressionMode mode,
    long sizeBytes,
    double compressionPercentage
) {
}
