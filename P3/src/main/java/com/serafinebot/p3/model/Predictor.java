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

    public record Constants(double bruteForce, double divideConquer, double farthest) {}

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
        int count = 0;

        for (Benchmark.BenchmarkEntry e : entries) {
            double n = e.n();
            if (n < 2) continue;

            double fQuad  = n * n;
            double fNLogN = n * log2(n);

            numBrute += e.bruteForce().averageTimeMs()    * fQuad;
            denBrute += fQuad  * fQuad;

            numDC    += e.divideConquer().averageTimeMs() * fNLogN;
            denDC    += fNLogN * fNLogN;

            numFar   += e.farthestPair().averageTimeMs()  * fNLogN;
            denFar   += fNLogN * fNLogN;
            count++;
        }

        if (count == 0) throw new IllegalArgumentException("No usable entries");

        return new Constants(numBrute / denBrute, numDC / denDC, numFar / denFar);
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

    /**
     * Returns the crossover point: smallest integer n where brute force becomes
     * slower than D&C. Returns -1 if D&C is never faster in [2, maxN].
     */
    public static long crossover(Constants c, long maxN) {
        // At small n brute force is faster (smaller constant); find where D&C wins.
        // Binary search for smallest n where predictBrute(n) > predictDC(n).
        if (predictBrute(c.bruteForce(), maxN) <= predictDC(c.divideConquer(), maxN))
            return -1; // D&C never wins up to maxN

        long lo = 2, hi = maxN;
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2;
            if (predictBrute(c.bruteForce(), mid) > predictDC(c.divideConquer(), mid)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    private static double log2(double n) {
        return Math.log(n) / Math.log(2);
    }
}
