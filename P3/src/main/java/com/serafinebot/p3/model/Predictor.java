package com.serafinebot.p3.model;

/**
 * Fits multiplicative constants from benchmark data and predicts execution times.
 *
 * For each algorithm the cost model f(n) is:
 *   Brute force:           f(n) = n²
 *   D&C:                   f(n) = n · log₂(n)²  (strip sorted at each level)
 *   D&C Bucket:            f(n) = n · log₂(n)
 *   Farthest pair:         f(n) = n · log₂(n)
 *
 * The constant a is estimated via weighted least-squares (no intercept):
 *   a = Σ(t_i · f(n_i)) / Σ(f(n_i)²)
 * This weights large-N samples more heavily, so the fit reflects asymptotic
 * behaviour rather than small-N JVM overhead.
 * Prediction: t(n) = a · f(n).
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

            double fQuad    = n * n;
            double logN     = log2(n);
            double fNLogN   = n * logN;
            double fNLog2N  = n * logN * logN;

            numBrute += e.bruteForce().averageTimeMs()            * fQuad;
            denBrute += fQuad  * fQuad;

            numDC    += e.divideConquer().averageTimeMs()          * fNLog2N;
            denDC    += fNLog2N * fNLog2N;

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
        double logN = log2(n);
        return a * n * logN * logN;
    }

    public static double predictFarthest(double a, long n) {
        return a * n * log2(n);
    }

    public static double predictBucket(double a, long n) {
        return a * n * log2(n);
    }

    private static double log2(double n) {
        return Math.log(n) / Math.log(2);
    }
}
