package com.serafinebot.p3.model;

/**
 * Fits multiplicative constants from benchmark data and predicts execution times.
 *
 * For each algorithm the theoretical cost function f(n) is known:
 *   Brute force:    f(n) = n²
 *   D&C / Farthest: f(n) = n · log₂(n)
 *
 * The constant a = mean(t_i / f(n_i)) over all measurements.
 * Prediction: t(n) = a · f(n).
 *
 * Crossover: smallest n where bruteForce(n) > divideConquer(n), i.e.
 *   a_brute · n² = a_dc · n · log₂(n)  →  a_brute · n = a_dc · log₂(n)
 * Solved with binary search.
 */
public class Predictor {

    public record Constants(double bruteForce, double divideConquer, double farthest, double divideConquerBucket) {}

    public static Constants fit(Benchmark.BenchmarkEntry[] entries) {
        if (entries == null || entries.length == 0)
            throw new IllegalArgumentException("No benchmark data to fit");

        // Least-squares fit for model t = a·f(n) with no intercept:
        //   a = Σ(t_i · f(n_i)) / Σ(f(n_i)²)
        // This weights large-N measurements more heavily, giving a constant that
        // reflects asymptotic behaviour rather than small-N JVM overhead.
        double numBrute = 0, denBrute = 0;
        double numDC = 0,    denDC = 0;
        double numFar = 0,   denFar = 0;
        double numDCB = 0,   denDCB = 0;
        int count = 0;

        for (Benchmark.BenchmarkEntry e : entries) {
            double n = e.n();
            if (n < 2) continue;

            double fQuad  = n * n;
            double fNLogN = n * log2(n);

            numBrute += e.bruteForce().averageTimeMs()            * fQuad;
            denBrute += fQuad  * fQuad;

            numDC    += e.divideConquer().averageTimeMs()          * fNLogN;
            denDC    += fNLogN * fNLogN;

            numFar   += e.farthestPair().averageTimeMs()           * fNLogN;
            denFar   += fNLogN * fNLogN;

            numDCB   += e.divideConquerBucket().averageTimeMs()    * fNLogN;
            denDCB   += fNLogN * fNLogN;
            count++;
        }

        if (count == 0) throw new IllegalArgumentException("No usable entries");

        return new Constants(numBrute / denBrute, numDC / denDC, numFar / denFar, numDCB / denDCB);
    }

    public static double predictBrute(double a, long n) {
        return a * n * n;
    }

    public static double predictDC(double a, long n) {
        return a * n * log2(n);
    }

    public static double predictFarthest(double a, long n) {
        return a * n * log2(n);
    }

    private static double log2(double n) {
        return Math.log(n) / Math.log(2);
    }
}
