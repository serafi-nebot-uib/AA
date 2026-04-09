package com.serafinebot.p3.model;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Benchmark {

    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 5;
    private static final int JIT_WARMUP_N = 500;
    private static final int JIT_WARMUP_ITERS = 10;

    public record Result(double averageTimeMs, PointPair pair) {
    }

    /**
     * Benchmarks a closest/farthest pair algorithm.
     * Discards the first WARMUP_RUNS executions, then averages MEASURED_RUNS.
     * Follows the methodology from the class notes (slide 125).
     */
    public static Result run(PairFinder finder, Point[] points) {
        PointPair result = null;

        // Warmup: discard first 3 runs (cache, JIT)
        for (int i = 0; i < WARMUP_RUNS; i++) {
            if (Thread.currentThread().isInterrupted())
                throw new RuntimeException(new InterruptedException("Cancelled"));
            result = finder.find(points);
        }

        // Measured runs: average of 5
        long totalNanos = 0;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            if (Thread.currentThread().isInterrupted())
                throw new RuntimeException(new InterruptedException("Cancelled"));
            long start = System.nanoTime();
            result = finder.find(points);
            long end = System.nanoTime();
            totalNanos += (end - start);
        }

        double avgMs = (totalNanos / (double) MEASURED_RUNS) / 1_000_000.0;
        return new Result(avgMs, result);
    }

    /**
     * Runs benchmarks for a range of N values using fine-grained parallelism:
     * one task per (algorithm, N) pair — 3 × nValues.length tasks total.
     * This prevents the largest-N brute-force run from serialising D&C and farthest
     * on a single thread while other cores sit idle.
     *
     * Point arrays are pre-generated on the main thread with deterministic per-slot
     * seeds so results are reproducible. Arrays are read-only inside tasks — no
     * synchronisation needed.
     */
    @SuppressWarnings("unchecked")
    public static BenchmarkEntry[] runSeries(int[] nValues, PointCloud.Distribution distribution,
                                              double rangeMin, double rangeMax, DistributionParams params) {
        jitWarmup(distribution, rangeMin, rangeMax, params);

        // Pre-generate all point arrays
        Point[][] allPoints = new Point[nValues.length][];
        for (int i = 0; i < nValues.length; i++) {
            PointCloud cloud = new PointCloud();
            allPoints[i] = cloud.generate(nValues[i], distribution, rangeMin, rangeMax, params);
        }

        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // futures[i][0] = brute, [1] = D&C, [2] = farthest, [3] = D&C bucket
        Future<Result>[][] futures = new Future[nValues.length][4];
        for (int i = 0; i < nValues.length; i++) {
            Point[] pts = allPoints[i];
            futures[i][0] = pool.submit(() -> run(BruteForceClosest::find,            pts));
            futures[i][1] = pool.submit(() -> run(DivideConquerClosest::find,         pts));
            futures[i][2] = pool.submit(() -> run(FarthestPair::find,                 pts));
            futures[i][3] = pool.submit(() -> run(DivideConquerClosestBucket::find,   pts));
        }

        pool.shutdown();

        BenchmarkEntry[] entries = new BenchmarkEntry[nValues.length];
        for (int i = 0; i < nValues.length; i++) {
            try {
                entries[i] = new BenchmarkEntry(
                    nValues[i],
                    futures[i][0].get(),
                    futures[i][1].get(),
                    futures[i][2].get(),
                    futures[i][3].get()
                );
            } catch (Exception e) {
                throw new RuntimeException("Benchmark task failed for slot " + i, e);
            }
        }

        return entries;
    }

    /**
     * Triggers JIT compilation of all three algorithm call paths before any parallel
     * tasks are submitted. JIT compilation is JVM-wide, so running here on the main
     * thread is enough to warm up every worker thread that follows.
     *
     * Uses the same distribution as the actual benchmark so the same code paths
     * (e.g. rejection-sampling branches in PointCloud) are compiled too.
     */
    private static void jitWarmup(PointCloud.Distribution distribution, double rangeMin, double rangeMax, DistributionParams params) {
        Point[] pts = new PointCloud().generate(JIT_WARMUP_N, distribution, rangeMin, rangeMax, params);
        for (int i = 0; i < JIT_WARMUP_ITERS; i++) {
            BruteForceClosest.find(pts);
            DivideConquerClosest.find(pts);
            FarthestPair.find(pts);
            DivideConquerClosestBucket.find(pts);
        }
    }

    public record BenchmarkEntry(int n, Result bruteForce, Result divideConquer, Result farthestPair, Result divideConquerBucket) {
    }
}
