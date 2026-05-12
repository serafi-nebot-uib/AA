package com.serafinebot.p5;

import com.serafinebot.p5.analysis.SolverBenchmark;
import com.serafinebot.p5.analysis.SolverMeasurement;
import com.serafinebot.p5.model.GameInput;

/**
 * Console benchmark entry point for report data collection.
 *
 * <p>Run with {@code mvn exec:java -Dexec.mainClass="com.serafinebot.p5.BenchmarkMain"}.
 * Optional arguments are {@code warmups repetitions}; defaults are {@code 3 10}.
 */
public final class BenchmarkMain {

    private BenchmarkMain() {}

    public static void main(String[] args) {
        int warmups = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int repetitions = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        System.out.println(SolverMeasurement.csvHeader());
        for (GameInput input : SolverBenchmark.defaultInputs()) {
            for (SolverMeasurement measurement : SolverBenchmark.measureAll(input, warmups, repetitions)) {
                System.out.println(measurement.toCsvRow());
            }
        }
    }
}
