package com.serafinebot.p4.model.benchmark;

import java.nio.file.Path;

/**
 * Configuration for a benchmark run.
 *
 * @param corpusDirectory directory containing the corpus files to benchmark
 * @param repetitions number of repetitions for each file/strategy combination
 */
public record BenchmarkConfig(
    Path corpusDirectory,
    int repetitions
) {
    public BenchmarkConfig {
        if (corpusDirectory == null) {
            throw new IllegalArgumentException("El directori del corpus no pot ser nul.");
        }
        if (repetitions <= 0) {
            throw new IllegalArgumentException("El nombre de repeticions ha de ser positiu.");
        }
    }
}
