package com.serafinebot.p7.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * Summary statistics required by the assignment output format.
 *
 * <p>The class stores all values that must be printed by the mandatory CLI:
 * minimum, empirical percentiles 0.1 through 0.9, median, maximum and mean. The
 * percentile convention is nearest-rank, so each percentile is an observed turn
 * count rather than an interpolated decimal.</p>
 */
public final class SimulationStats {

    private final int minimum;
    private final int p1;
    private final int p2;
    private final int p3;
    private final int p4;
    private final int median;
    private final int p6;
    private final int p7;
    private final int p8;
    private final int p9;
    private final int maximum;
    private final double mean;

    private SimulationStats(int minimum, int p1, int p2, int p3, int p4, int median,
                            int p6, int p7, int p8, int p9, int maximum, double mean) {
        this.minimum = minimum;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.p4 = p4;
        this.median = median;
        this.p6 = p6;
        this.p7 = p7;
        this.p8 = p8;
        this.p9 = p9;
        this.maximum = maximum;
        this.mean = mean;
    }

    /**
     * Builds statistics from positive turn-count observations.
     */
    public static SimulationStats from(int[] observations) {
        if (observations == null || observations.length == 0) {
            throw new IllegalArgumentException("At least one observation is required.");
        }

        int[] sorted = observations.clone();
        Arrays.sort(sorted);

        long sum = 0L;
        for (int observation : observations) {
            if (observation <= 0) {
                throw new IllegalArgumentException("Turn counts must be positive.");
            }
            sum += observation;
        }

        return new SimulationStats(
                sorted[0],
                nearestRank(sorted, 0.1),
                nearestRank(sorted, 0.2),
                nearestRank(sorted, 0.3),
                nearestRank(sorted, 0.4),
                nearestRank(sorted, 0.5),
                nearestRank(sorted, 0.6),
                nearestRank(sorted, 0.7),
                nearestRank(sorted, 0.8),
                nearestRank(sorted, 0.9),
                sorted[sorted.length - 1],
                sum / (double) observations.length
        );
    }

    /**
     * Returns the nearest-rank percentile from an already sorted sample.
     */
    private static int nearestRank(int[] sorted, double percentile) {
        int rank = (int) Math.ceil(percentile * sorted.length);
        int index = Math.max(0, Math.min(sorted.length - 1, rank - 1));
        return sorted[index];
    }

    /**
     * Formats the exact labels required by the assignment statement.
     */
    public String formatRequiredOutput() {
        return String.format(Locale.US,
                "Minimo: %d%n" +
                        "P1: %d%n" +
                        "P2: %d%n" +
                        "P3: %d%n" +
                        "P4: %d%n" +
                        "Mediana: %d%n" +
                        "P6: %d%n" +
                        "P7: %d%n" +
                        "P8: %d%n" +
                        "P9: %d%n" +
                        "Maximo: %d%n" +
                        "Media: %.6f%n",
                minimum, p1, p2, p3, p4, median, p6, p7, p8, p9, maximum, mean);
    }

    public int minimum() {
        return minimum;
    }

    public int p1() {
        return p1;
    }

    public int p2() {
        return p2;
    }

    public int p3() {
        return p3;
    }

    public int p4() {
        return p4;
    }

    public int median() {
        return median;
    }

    public int p6() {
        return p6;
    }

    public int p7() {
        return p7;
    }

    public int p8() {
        return p8;
    }

    public int p9() {
        return p9;
    }

    public int maximum() {
        return maximum;
    }

    public double mean() {
        return mean;
    }
}
