package com.serafinebot.p4.model.benchmark;

/**
 * Configuration for a benchmark run.
 *
 * @param minSizeBytes smallest generated file size in bytes
 * @param maxSizeBytes largest generated file size in bytes
 * @param pointCount number of sizes to evaluate between minimum and maximum
 * @param repetitions number of repetitions for each size/profile/strategy combination
 */
public record BenchmarkConfig(
    int minSizeBytes,
    int maxSizeBytes,
    int pointCount,
    int repetitions
) {
    public BenchmarkConfig {
        if (minSizeBytes <= 0) {
            throw new IllegalArgumentException("La mida minima ha de ser positiva.");
        }
        if (maxSizeBytes < minSizeBytes) {
            throw new IllegalArgumentException("La mida maxima ha de ser major o igual que la minima.");
        }
        if (pointCount <= 0) {
            throw new IllegalArgumentException("El nombre de punts ha de ser positiu.");
        }
        if (repetitions <= 0) {
            throw new IllegalArgumentException("El nombre de repeticions ha de ser positiu.");
        }
    }
}
