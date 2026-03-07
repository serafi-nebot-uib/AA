package com.serafinebot.p1.controller;

import com.serafinebot.p1.model.AlgorithmType;
import com.serafinebot.p1.model.Measurement;
import com.serafinebot.p1.model.Model;
import com.serafinebot.p1.view.MainView;
import com.serafinebot.p1.view.ViewListener;

import javax.swing.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador MVC. Implementa {@link ViewListener} per rebre events
 * de la vista sense accedir a cap element visual. Gestiona l'execucio
 * dels algorismes en fils de fons (SwingWorker).
 */
public class Controller implements ViewListener {

    private final Model model;
    private final MainView view;
    private final Map<AlgorithmType, SwingWorker<Void, Measurement>> workers = new EnumMap<>(AlgorithmType.class);

    public Controller(Model model, MainView view) {
        this.model = model;
        this.view = view;
        view.addViewListener(this);
    }

    // ---- Implementacio de ViewListener ----

    @Override
    public void onStartAlgorithm(AlgorithmType type, long[] nValues) {
        if (!isRunning(type)) {
            startExecution(type, nValues);
        }
    }

    @Override
    public void onStopAlgorithm(AlgorithmType type) {
        stopExecution(type);
    }

    @Override
    public void onStartAll(long[] nValues) {
        for (AlgorithmType type : AlgorithmType.values()) {
            if (!isRunning(type)) {
                startExecution(type, nValues);
            }
        }
    }

    @Override
    public void onStopAll() {
        for (AlgorithmType type : AlgorithmType.values()) {
            stopExecution(type);
        }
    }

    @Override
    public void onPredict(long predictN) {
        Map<AlgorithmType, Double> predictions = new EnumMap<>(AlgorithmType.class);
        for (AlgorithmType type : AlgorithmType.values()) {
            predictions.put(type, model.predict(type, predictN));
        }
        view.showPredictions(predictN, predictions);
    }

    @Override
    public void onClear() {
        onStopAll();
        model.clearAll();
    }

    @Override
    public void onLogScaleChanged(boolean logScale) {
        view.setLogScale(logScale);
    }

    // ---- Logica interna ----

    private void startExecution(AlgorithmType type, long[] nValues) {
        SwingWorker<Void, Measurement> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                // Escalfament per a valors petits (reduïr overhead JIT per a execucions reals)
                try {
                    type.execute(500);
                } catch (InterruptedException ignored) {
                }

                for (long n : nValues) {
                    if (isCancelled()) break;

                    // Mesura real: mínim de 5 repeticions per reduir soroll
                    long minElapsed = Long.MAX_VALUE;
                    for (int rep = 0; rep < 5; rep++) {
                        if (isCancelled()) break;
                        long start = System.nanoTime();
                        try {
                            type.execute(n);
                        } catch (InterruptedException e) {
                            break;
                        }
                        long elapsed = System.nanoTime() - start;
                        if (elapsed < minElapsed) minElapsed = elapsed;
                    }
                    if (isCancelled()) break;

                    publish(new Measurement(type, n, minElapsed));
                }
                return null;
            }

            @Override
            protected void process(List<Measurement> chunks) {
                for (Measurement m : chunks) {
                    model.addMeasurement(m);
                }
            }

            @Override
            protected void done() {
                workers.remove(type);
                view.setAlgorithmRunning(type, false);
            }
        };

        model.clearMeasurements(type);
        workers.put(type, worker);
        view.setAlgorithmRunning(type, true);
        worker.execute();
    }

    private void stopExecution(AlgorithmType type) {
        SwingWorker<?, ?> worker = workers.get(type);
        if (worker != null) {
            worker.cancel(true);
            workers.remove(type);
            view.setAlgorithmRunning(type, false);
        }
    }

    private boolean isRunning(AlgorithmType type) {
        SwingWorker<?, ?> w = workers.get(type);
        return w != null && !w.isDone();
    }
}
