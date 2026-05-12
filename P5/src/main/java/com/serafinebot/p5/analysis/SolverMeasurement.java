package com.serafinebot.p5.analysis;

import com.serafinebot.p5.model.SolverMode;

/**
 * One aggregated benchmark measurement for a solver and input configuration.
 *
 * <p>Time values are averages over the measured repetitions. Heap usage is an
 * approximation based on the JVM heap difference before and after each run; it
 * is useful for trends in the report, not as an exact allocation profiler.
 */
public record SolverMeasurement(
        SolverMode solverMode,
        int width,
        int height,
        int keyCount,
        int limit,
        int playerCount,
        int initialTotal,
        int lastPlayed,
        int startingPlayer,
        int legalMoveUpperBound,
        int fullStateCount,
        int computedStates,
        int losingPlayer,
        int replayLength,
        long wallNanos,
        long cpuNanos,
        long heapDeltaBytes
) {
    public static String csvHeader() {
        return "solver,width,height,keys,limit,players,initial,last,starting,branch_upper_bound,full_states," +
                "computed_states,losing_player,replay_length,wall_ns,cpu_ns,heap_delta_bytes";
    }

    public String toCsvRow() {
        return solverMode + "," + width + "," + height + "," + keyCount + "," + limit + "," + playerCount + "," +
                initialTotal + "," + lastPlayed + "," + startingPlayer + "," + legalMoveUpperBound + "," +
                fullStateCount + "," + computedStates + "," + losingPlayer + "," + replayLength + "," +
                wallNanos + "," + cpuNanos + "," + heapDeltaBytes;
    }
}
