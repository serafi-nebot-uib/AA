package com.serafinebot.p3.model;

import java.util.function.Supplier;

public class Benchmark {

    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 5;

    public record Result(double averageTimeMs, PointPair pair) {
    }

    /**
     * Benchmarks a closest/farthest pair algorithm.
     * Discards the first WARMUP_RUNS executions, then averages MEASURED_RUNS.
     * Follows the methodology from the class notes (slide 125).
     */
    public static Result run(Supplier<PointPair> algorithm) {
        PointPair result = null;

        // Warmup: discard first 3 runs (cache, JIT)
        for (int i = 0; i < WARMUP_RUNS; i++) {
            result = algorithm.get();
        }

        // Measured runs: average of 5
        long totalNanos = 0;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            result = algorithm.get();
            long end = System.nanoTime();
            totalNanos += (end - start);
        }

        double avgMs = (totalNanos / (double) MEASURED_RUNS) / 1_000_000.0;
        return new Result(avgMs, result);
    }

    /**
     * Runs benchmarks for a range of N values.
     * Returns an array of BenchmarkEntry for each N.
     */
    public static BenchmarkEntry[] runSeries(int[] nValues, PointCloud.Distribution distribution,
                                              double rangeMin, double rangeMax) {
        // TODO: disable fixed seed
        PointCloud cloud = new PointCloud(42); // fixed seed for reproducibility
        BenchmarkEntry[] entries = new BenchmarkEntry[nValues.length];

        for (int i = 0; i < nValues.length; i++) {
            int n = nValues[i];
            Point[] points = cloud.generate(n, distribution, rangeMin, rangeMax);

            Result bruteResult = run(() -> BruteForceClosest.find(points));
            Result dcResult = run(() -> DivideConquerClosest.find(points));
            Result farthestResult = run(() -> FarthestPair.find(points));

            entries[i] = new BenchmarkEntry(n, bruteResult, dcResult, farthestResult);
        }

        return entries;
    }

    public record BenchmarkEntry(int n, Result bruteForce, Result divideConquer, Result farthestPair) {
    }
}
