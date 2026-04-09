package com.serafinebot.p3.controller;

import com.serafinebot.p3.model.*;
import com.serafinebot.p3.model.PointCloud.Distribution;
import com.serafinebot.p3.view.BenchmarkWindow;
import com.serafinebot.p3.view.MainView;
import com.serafinebot.p3.view.ViewListener;

import javax.swing.*;

public class Controller implements ViewListener {

    private final Model model;
    private final MainView view;
    private BenchmarkWindow benchmarkWindow;

    public Controller(Model model, MainView view) {
        this.model = model;
        this.view = view;
        view.addViewListener(this);
    }

    // ---- ViewListener implementation ----

    @Override
    public void onGenerate(int n, Distribution distribution, double rangeMin, double rangeMax) {
        model.generatePoints(n, distribution, rangeMin, rangeMax);
        view.showPoints(rangeMin, rangeMax);
        view.clearResults();
        view.setRunButtonEnabled(true);
    }

    @Override
    public void onRun() {
        view.setRunning(true);
        Point[] points = model.getPoints();

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                Benchmark.Result bruteResult    = Benchmark.run(BruteForceClosest::find, points);
                Benchmark.Result dcResult       = Benchmark.run(DivideConquerClosest::find, points);
                Benchmark.Result farthestResult = Benchmark.run(FarthestPair::find, points);

                model.setResults(
                    bruteResult.pair(),    bruteResult.averageTimeMs(),
                    dcResult.pair(),       dcResult.averageTimeMs(),
                    farthestResult.pair(), farthestResult.averageTimeMs()
                );
                return null;
            }

            @Override
            protected void done() {
                view.updateResults();
                view.setRunning(false);
            }
        };
        worker.execute();
    }

    @Override
    public void onOpenBenchmark() {
        if (benchmarkWindow == null) {
            benchmarkWindow = new BenchmarkWindow(view, this);
        }
        benchmarkWindow.setVisible(true);
        benchmarkWindow.toFront();
    }

    // ---- Called by BenchmarkWindow ----

    public void runBenchmarkSeries(int[] nValues, Distribution distribution, double rangeMin, double rangeMax) {
        Benchmark.BenchmarkEntry[] results =
            Benchmark.runSeries(nValues, distribution, rangeMin, rangeMax);
        model.setBenchmarkResults(results);
    }

    public Benchmark.BenchmarkEntry[] getBenchmarkResults() {
        return model.getBenchmarkResults();
    }
}
