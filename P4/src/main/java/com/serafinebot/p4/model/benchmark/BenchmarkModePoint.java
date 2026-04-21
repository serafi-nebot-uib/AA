package com.serafinebot.p4.model.benchmark;

/**
 * One benchmark measurement focused on compression variant comparison.
 *
 * @param sourceName corpus file name used for the measurement
 * @param variant compression variant used for the run
 * @param sizeBytes input file size in bytes
 * @param compressionPercentage average compression gain in percent
 * @param compressionMillis average wall-clock compression time in milliseconds
 * @param decompressionMillis average wall-clock decompression time in milliseconds
 */
public record BenchmarkModePoint(
    String sourceName,
    BenchmarkVariant variant,
    long sizeBytes,
    double compressionPercentage,
    double compressionMillis,
    double decompressionMillis
) {
}
