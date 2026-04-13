package com.serafinebot.p3.controller;

import com.serafinebot.p3.model.*;
import com.serafinebot.p3.model.PointCloud.Distribution;
import com.serafinebot.p3.view.BenchmarkWindow;
import com.serafinebot.p3.view.MainView;
import com.serafinebot.p3.view.ViewListener;

import javax.swing.*;

/**
 * MVC controller. Wires the view events to model operations and manages
 * the background SwingWorker for algorithm execution.
 */
public class Controller implements ViewListener {

    private final Model model;
    private final MainView view;
    private BenchmarkWindow benchmarkWindow;
    private SwingWorker<?, ?> runWorker;

    public Controller(Model model, MainView view) {
        this.model = model;
        this.view = view;
        view.addViewListener(this);
    }

    // ---- ViewListener implementation ----

    @Override
    public void onGenerate(int n, Distribution distribution, double rangeMin, double rangeMax, DistributionParams params) {
        model.generatePoints(n, distribution, rangeMin, rangeMax, params);
        view.showPoints(rangeMin, rangeMax);
        view.clearResults();
        view.setRunButtonEnabled(true);
    }

    @Override
    public void onRun() {
        view.setRunning(true);
        Point[] points = model.getPoints();

        runWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                Benchmark.Result bruteResult    = Benchmark.run(Benchmark.BRUTE,    points);
                Benchmark.Result dcResult       = Benchmark.run(Benchmark.DC,       points);
                Benchmark.Result bucketResult   = Benchmark.run(Benchmark.BUCKET,   points);
                Benchmark.Result farthestResult = Benchmark.run(Benchmark.FARTHEST, points);

                model.setResults(
                    bruteResult.pair(),    bruteResult.averageTimeMs(),
                    dcResult.pair(),       dcResult.averageTimeMs(),
                    bucketResult.pair(),   bucketResult.averageTimeMs(),
                    farthestResult.pair(), farthestResult.averageTimeMs()
                );
                return null;
            }

            @Override
            protected void done() {
                if (!isCancelled()) view.updateResults();
                view.setRunning(false);
            }
        };
        runWorker.execute();
    }

    @Override
    public void onStop() {
        if (runWorker != null) runWorker.cancel(true);
    }

    @Override
    public void onOpenBenchmark() {
        if (benchmarkWindow == null) {
            benchmarkWindow = new BenchmarkWindow(view, this);
        }
        benchmarkWindow.setVisible(true);
        benchmarkWindow.toFront();
    }

    // ---- ViewListener: benchmark ----

    @Override
    public Benchmark.BenchmarkEntry[] onRunBenchmark(int[] nValues, Distribution distribution,
                                                      double rangeMin, double rangeMax,
                                                      DistributionParams params) {
        Benchmark.BenchmarkEntry[] results =
            Benchmark.runSeries(nValues, distribution, rangeMin, rangeMax, params);
        model.setBenchmarkResults(results);
        return results;
    }
}
