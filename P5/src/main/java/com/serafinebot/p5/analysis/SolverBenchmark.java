package com.serafinebot.p5.analysis;

import com.serafinebot.p5.model.BottomUpDPSolver;
import com.serafinebot.p5.model.GameConfig;
import com.serafinebot.p5.model.GameInput;
import com.serafinebot.p5.model.Keypad;
import com.serafinebot.p5.model.KeypadMode;
import com.serafinebot.p5.model.Solver;
import com.serafinebot.p5.model.SolverMode;
import com.serafinebot.p5.model.TopDownDPSolver;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Benchmark helper used to collect data for the solver-analysis report section.
 *
 * <p>The benchmark measures only solver construction and solving/replay work;
 * Swing rendering is intentionally excluded. Results should be interpreted as
 * comparative measurements between solver modes on the same machine and JVM.
 */
public final class SolverBenchmark {

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private SolverBenchmark() {}

    /**
     * Builds a compact set of inputs that grows both keypad size and losing
     * limit. This keeps the default benchmark fast while still showing trends.
     */
    public static List<GameInput> defaultInputs() {
        List<GameInput> inputs = new ArrayList<>();
        for (int size = 2; size <= 5; size++) {
            for (int limit : new int[]{31, 60, 100}) {
                inputs.add(new GameInput(size, size, 0, limit, 0, 2, 1));
            }
        }
        return inputs;
    }

    /** Measures every solver mode for the given input. */
    public static List<SolverMeasurement> measureAll(GameInput input, int warmups, int repetitions) {
        List<SolverMeasurement> measurements = new ArrayList<>();
        for (SolverMode mode : SolverMode.values()) {
            measurements.add(measure(input, mode, warmups, repetitions));
        }
        return measurements;
    }

    /**
     * Measures one solver mode using warmup runs followed by averaged measured
     * repetitions.
     */
    public static SolverMeasurement measure(GameInput input, SolverMode mode, int warmups, int repetitions) {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        if (warmups < 0) throw new IllegalArgumentException("warmups must be >= 0");
        if (repetitions < 1) throw new IllegalArgumentException("repetitions must be >= 1");

        ensureCpuTimeEnabled();
        for (int i = 0; i < warmups; i++) run(input, mode);

        long wallTotal = 0;
        long cpuTotal = 0;
        long heapTotal = 0;
        RunResult last = null;
        for (int i = 0; i < repetitions; i++) {
            long heapBefore = usedHeap();
            long cpuBefore = currentThreadCpuTime();
            long wallBefore = System.nanoTime();
            last = run(input, mode);
            long wallAfter = System.nanoTime();
            long cpuAfter = currentThreadCpuTime();
            long heapAfter = usedHeap();

            wallTotal += wallAfter - wallBefore;
            if (cpuBefore >= 0 && cpuAfter >= 0) cpuTotal += cpuAfter - cpuBefore;
            heapTotal += heapAfter - heapBefore;
        }

        int keyCount = input.width() * input.height();
        return new SolverMeasurement(
                mode,
                input.width(),
                input.height(),
                keyCount,
                input.limit(),
                input.playerCount(),
                input.initialTotal(),
                input.lastPlayed(),
                input.startingPlayer(),
                Math.max(keyCount, input.width() + input.height() - 2),
                input.limit() * (keyCount + 1) * input.playerCount(),
                last.computedStates(),
                last.losingPlayer(),
                last.replayLength(),
                wallTotal / repetitions,
                cpuTotal == 0 ? -1 : cpuTotal / repetitions,
                heapTotal / repetitions
        );
    }

    private static RunResult run(GameInput input, SolverMode mode) {
        Keypad keypad = input.keypadMode() == KeypadMode.RANDOM
                ? Keypad.random(input.width(), input.height(), new Random(input.keypadSeed()))
                : Keypad.standard(input.width(), input.height());
        GameConfig config = new GameConfig(
                keypad,
                input.initialTotal(),
                input.limit(),
                input.lastPlayed(),
                input.playerCount(),
                input.startingPlayer() - 1
        );
        Solver solver = mode == SolverMode.BOTTOM_UP_DP ? new BottomUpDPSolver(config) : new TopDownDPSolver(config);
        int losingPlayer = solver.losingPlayer() + 1;
        int replayLength = solver.replay().size();
        return new RunResult(losingPlayer, replayLength, solver.stateCount());
    }

    private static void ensureCpuTimeEnabled() {
        if (THREADS.isCurrentThreadCpuTimeSupported() && !THREADS.isThreadCpuTimeEnabled()) {
            THREADS.setThreadCpuTimeEnabled(true);
        }
    }

    private static long currentThreadCpuTime() {
        if (!THREADS.isCurrentThreadCpuTimeSupported() || !THREADS.isThreadCpuTimeEnabled()) return -1;
        return THREADS.getCurrentThreadCpuTime();
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private record RunResult(int losingPlayer, int replayLength, int computedStates) {}
}
