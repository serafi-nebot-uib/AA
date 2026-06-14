package com.serafinebot.p7.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Complete result of one Monte Carlo batch, including data for the GUI.
 *
 * <p>This is a presentation-oriented wrapper around raw observations and their
 * statistics. It also carries metadata that matters for reproducibility: seed,
 * selected rule variant, thread count and optional variant-comparison results.</p>
 */
public final class SimulationResult {

    private final SimulationStats stats;
    private final int[] turnCounts;
    private final long[] squareVisits;
    private final int[] winnerCounts;
    private final long elapsedMillis;
    private final long seed;
    private final RuleVariant variant;
    private final int threadCount;
    private final Map<RuleVariant, SimulationStats> comparisonStats;

    public SimulationResult(SimulationStats stats, int[] turnCounts, long elapsedMillis, long seed) {
        this(stats, turnCounts, new long[Game.FINISH + 1], new int[]{turnCounts.length}, elapsedMillis, seed,
                RuleVariant.STANDARD, 1, Map.of());
    }

    /**
     * Creates a GUI/report result and defensively copies mutable collections.
     */
    public SimulationResult(SimulationStats stats, int[] turnCounts, long[] squareVisits, int[] winnerCounts,
                            long elapsedMillis, long seed, RuleVariant variant, int threadCount,
                            Map<RuleVariant, SimulationStats> comparisonStats) {
        this.stats = Objects.requireNonNull(stats);
        this.turnCounts = Objects.requireNonNull(turnCounts).clone();
        this.squareVisits = Objects.requireNonNull(squareVisits).clone();
        this.winnerCounts = Objects.requireNonNull(winnerCounts).clone();
        this.elapsedMillis = elapsedMillis;
        this.seed = seed;
        this.variant = Objects.requireNonNull(variant);
        this.threadCount = threadCount;
        this.comparisonStats = new LinkedHashMap<>(Objects.requireNonNull(comparisonStats));
    }

    public SimulationStats stats() {
        return stats;
    }

    public int[] turnCounts() {
        return turnCounts.clone();
    }

    public long[] squareVisits() {
        return squareVisits.clone();
    }

    public int[] winnerCounts() {
        return winnerCounts.clone();
    }

    public int playerCount() {
        return winnerCounts.length;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public long seed() {
        return seed;
    }

    public RuleVariant variant() {
        return variant;
    }

    public int threadCount() {
        return threadCount;
    }

    public Map<RuleVariant, SimulationStats> comparisonStats() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(comparisonStats));
    }
}
