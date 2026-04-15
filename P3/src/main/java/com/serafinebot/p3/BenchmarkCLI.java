package com.serafinebot.p3;

import com.serafinebot.p3.model.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Command-line benchmark runner for generating report data.
 *
 * Usage:
 *   java -cp target/classes com.serafinebot.p3.BenchmarkCLI [options]
 *
 * Options:
 *   -d, --dist    UNIFORM|GAUSSIAN|EXPONENTIAL|CLUSTERED  (default: UNIFORM)
 *   -p, --params  comma-separated distribution params      (default: distribution defaults)
 *   -n, --nvals   comma-separated N values                 (default: 100,500,1000,2000,5000,10000,20000,50000)
 *   -a, --algos   comma-separated algorithms to run        (default: all)
 *                  choices: brute, dc, bucket, farthest
 *   -r, --runs    number of independent runs to average     (default: 1)
 *   --csv         output CSV instead of table
 *   --help        show this help
 *
 * Examples:
 *   # Only brute and D&C
 *   java -cp target/classes com.serafinebot.p3.BenchmarkCLI -a brute,dc
 *
 *   # Compare D&C variants on stretched Gaussian
 *   java -cp target/classes com.serafinebot.p3.BenchmarkCLI -a dc,bucket -d GAUSSIAN -p 0.5,0.1,1,4 --csv
 *
 *   # All algorithms, clustered, 3 averaged runs
 *   java -cp target/classes com.serafinebot.p3.BenchmarkCLI -d CLUSTERED -p 10 -n 1000,5000,10000,50000 -r 3
 */
public class BenchmarkCLI {

    private static final double RANGE_MIN = 0;
    private static final double RANGE_MAX = 1000;
    private static final int[] DEFAULT_N = {100, 500, 1000, 2000, 5000, 10000, 20000, 50000};
    private static final int JIT_WARMUP_N = 500;
    private static final int JIT_WARMUP_ITERS = 10;

    private record Algo(String key, String label, PairFinder finder) {}

    private static final Algo[] ALL_ALGOS = {
        new Algo("brute",   "Brute (ms)",      new BruteForceClosest()),
        new Algo("dc",      "D&C (ms)",        new DivideConquerClosest()),
        new Algo("bucket",  "D&C Bucket (ms)", new DivideConquerClosestBucket()),
        new Algo("farthest","Farthest (ms)",   new FarthestPair()),
    };

    public static void main(String[] args) {
        Distribution dist = Distribution.UNIFORM;
        DistributionParams params = null;
        int[] nValues = DEFAULT_N;
        int runs = 1;
        boolean csv = false;
        Set<String> algoKeys = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d", "--dist" -> dist = Distribution.valueOf(args[++i].toUpperCase());
                case "-p", "--params" -> {
                    String[] parts = args[++i].split(",");
                    double[] vals = new double[parts.length];
                    for (int j = 0; j < parts.length; j++) vals[j] = Double.parseDouble(parts[j].trim());
                    params = new DistributionParams(vals);
                }
                case "-n", "--nvals" -> {
                    String[] parts = args[++i].split(",");
                    nValues = new int[parts.length];
                    for (int j = 0; j < parts.length; j++) nValues[j] = Integer.parseInt(parts[j].trim());
                }
                case "-a", "--algos" -> {
                    algoKeys = new LinkedHashSet<>();
                    for (String k : args[++i].split(",")) algoKeys.add(k.trim().toLowerCase());
                }
                case "-r", "--runs" -> runs = Integer.parseInt(args[++i]);
                case "--csv" -> csv = true;
                case "--help", "-h" -> { printHelp(); return; }
                default -> { System.err.println("Unknown option: " + args[i]); printHelp(); return; }
            }
        }

        if (params == null) params = DistributionParams.defaults(dist);

        // Resolve selected algorithms (preserve order)
        List<Algo> selected = new ArrayList<>();
        if (algoKeys == null) {
            Collections.addAll(selected, ALL_ALGOS);
        } else {
            for (Algo a : ALL_ALGOS) {
                if (algoKeys.contains(a.key)) selected.add(a);
            }
            if (selected.isEmpty()) {
                System.err.println("No valid algorithms. Choices: brute, dc, bucket, farthest");
                return;
            }
        }

        // Print configuration
        System.err.printf("Distribution: %s%n", dist);
        System.err.printf("Params:       %s%n", formatParams(dist, params));
        System.err.printf("N values:     %s%n", formatIntArray(nValues));
        System.err.printf("Algorithms:   %s%n", selected.stream().map(Algo::key).reduce((a, b) -> a + ", " + b).orElse(""));
        System.err.printf("Runs:         %d%n", runs);
        System.err.printf("Range:        [%.0f, %.0f]%n%n", RANGE_MIN, RANGE_MAX);

        // JIT warmup for selected algorithms
        System.err.print("JIT warmup...");
        Point[] warmupPts = new PointCloud().generate(JIT_WARMUP_N, dist, RANGE_MIN, RANGE_MAX, params);
        for (int i = 0; i < JIT_WARMUP_ITERS; i++) {
            for (Algo a : selected) a.finder.find(warmupPts);
        }
        System.err.println(" done");

        // results[algoIdx][nIdx][runIdx]
        double[][][] results = new double[selected.size()][nValues.length][runs];

        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int r = 0; r < runs; r++) {
            if (runs > 1) System.err.printf("Run %d/%d...%n", r + 1, runs);

            // Pre-generate all point arrays
            Point[][] allPoints = new Point[nValues.length][];
            for (int i = 0; i < nValues.length; i++) {
                allPoints[i] = new PointCloud().generate(nValues[i], dist, RANGE_MIN, RANGE_MAX, params);
            }

            // Submit tasks: one per (algo, N)
            @SuppressWarnings("unchecked")
            Future<Benchmark.Result>[][] futures = new Future[selected.size()][nValues.length];
            for (int a = 0; a < selected.size(); a++) {
                PairFinder finder = selected.get(a).finder;
                for (int i = 0; i < nValues.length; i++) {
                    Point[] pts = allPoints[i];
                    futures[a][i] = pool.submit(() -> Benchmark.run(finder, pts));
                }
            }

            // Collect
            for (int a = 0; a < selected.size(); a++) {
                for (int i = 0; i < nValues.length; i++) {
                    try {
                        results[a][i][r] = futures[a][i].get().averageTimeMs();
                    } catch (Exception e) {
                        throw new RuntimeException("Benchmark failed: " + selected.get(a).key + " n=" + nValues[i], e);
                    }
                }
            }
        }

        pool.shutdown();

        // Output
        if (csv) {
            printCSV(selected, nValues, results);
        } else {
            printTable(selected, nValues, results);
        }
    }

    private static void printCSV(List<Algo> algos, int[] nValues, double[][][] results) {
        StringBuilder header = new StringBuilder("n");
        for (Algo a : algos) header.append(",").append(a.key).append("_ms");
        System.out.println(header);

        for (int i = 0; i < nValues.length; i++) {
            StringBuilder row = new StringBuilder();
            row.append(nValues[i]);
            for (int a = 0; a < algos.size(); a++) {
                row.append(String.format(",%.6f", mean(results[a][i])));
            }
            System.out.println(row);
        }
    }

    private static void printTable(List<Algo> algos, int[] nValues, double[][][] results) {
        // Header
        StringBuilder fmt = new StringBuilder("%-10s");
        List<Object> headerArgs = new ArrayList<>();
        headerArgs.add("N");
        for (Algo a : algos) {
            fmt.append(" %14s");
            headerArgs.add(a.label);
        }
        System.out.printf(fmt + "%n", headerArgs.toArray());
        System.out.println("-".repeat(10 + 15 * algos.size()));

        // Rows
        for (int i = 0; i < nValues.length; i++) {
            StringBuilder rowFmt = new StringBuilder("%-10d");
            List<Object> rowArgs = new ArrayList<>();
            rowArgs.add(nValues[i]);
            for (int a = 0; a < algos.size(); a++) {
                rowFmt.append(" %14.4f");
                rowArgs.add(mean(results[a][i]));
            }
            System.out.printf(rowFmt + "%n", rowArgs.toArray());
        }
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static String formatParams(Distribution dist, DistributionParams params) {
        ParamSpec[] specs = dist.params();
        if (specs.length == 0) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < specs.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(specs[i].label()).append("=").append(params.get(i));
        }
        return sb.toString();
    }

    private static String formatIntArray(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static void printHelp() {
        System.err.println("""
            Usage: java -cp target/classes com.serafinebot.p3.BenchmarkCLI [options]

            Options:
              -d, --dist    UNIFORM|GAUSSIAN|EXPONENTIAL|CLUSTERED  (default: UNIFORM)
              -p, --params  comma-separated distribution params      (default: distribution defaults)
              -n, --nvals   comma-separated N values                 (default: 100,500,...,50000)
              -a, --algos   comma-separated algorithms               (default: all)
                            choices: brute, dc, bucket, farthest
              -r, --runs    number of independent runs to average     (default: 1)
              --csv         output CSV instead of table
              --help        show this help

            Distribution parameters (in order):
              UNIFORM:      (none)
              GAUSSIAN:     mean, sigma, multX, multY
              EXPONENTIAL:  factorX, factorY
              CLUSTERED:    numClusters
            """);
    }
}
