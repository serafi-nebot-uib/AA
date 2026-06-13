package com.serafinebot.p7.model;

import java.util.Objects;

/**
 * Raw observations produced by a Monte Carlo batch.
 *
 * <p>{@code turnCounts} stores one value per simulated game. {@code squareVisits}
 * stores the aggregated visits to squares 0 through 63. Both arrays are cloned on
 * input and output so callers cannot accidentally mutate stored results.</p>
 */
public record SimulationData(int[] turnCounts, long[] squareVisits) {

    public SimulationData {
        turnCounts = Objects.requireNonNull(turnCounts).clone();
        squareVisits = Objects.requireNonNull(squareVisits).clone();
    }

    @Override
    public int[] turnCounts() {
        return turnCounts.clone();
    }

    @Override
    public long[] squareVisits() {
        return squareVisits.clone();
    }
}
