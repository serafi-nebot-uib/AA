package com.serafinebot.p3.view;

import com.serafinebot.p3.model.Benchmark;
import com.serafinebot.p3.model.Distribution;
import com.serafinebot.p3.model.DistributionParams;

/**
 * All events that any view component can emit. The controller implements
 * this interface so no view ever needs to hold a reference to a controller type.
 */
public interface ViewListener {
    void onGenerate(int n, Distribution distribution, double rangeMin, double rangeMax, DistributionParams params);
    void onRun();
    void onStop();
    void onOpenBenchmark();

    /**
     * Run a full benchmark series and return the results.
     * Called from a background thread (SwingWorker inside BenchmarkWindow).
     */
    Benchmark.BenchmarkEntry[] onRunBenchmark(int[] nValues,
                                                Distribution distribution,
                                                double rangeMin, double rangeMax,
                                                DistributionParams params);
}
