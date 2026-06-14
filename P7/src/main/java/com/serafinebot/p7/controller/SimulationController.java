package com.serafinebot.p7.controller;

import com.serafinebot.p7.model.RuleVariant;
import com.serafinebot.p7.model.Simulation;
import com.serafinebot.p7.model.SimulationData;
import com.serafinebot.p7.model.SimulationResult;
import com.serafinebot.p7.model.SimulationStats;
import com.serafinebot.p7.view.SimulationView;
import com.serafinebot.p7.view.SimulationViewListener;

import javax.swing.SwingWorker;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * MVC controller that runs simulations outside the Swing event thread.
 *
 * <p>The controller is the only layer that knows both the Swing view contract and
 * the simulation model. It validates UI input, starts long-running simulations in
 * a {@link SwingWorker}, and converts raw model data into {@link SimulationResult}
 * objects for rendering.</p>
 */
public final class SimulationController implements SimulationViewListener {

    private final SimulationView view;
    private SwingWorker<SimulationResult, Void> worker;

    public SimulationController(SimulationView view) {
        this.view = Objects.requireNonNull(view);
        this.view.setListener(this);
    }

    /**
     * Handles the Run button from the GUI.
     *
     * <p>If variant comparison is enabled, the selected variant is simulated once
     * and reused in the comparison table, while the other variants are simulated
     * with the same {@code N}, seed and thread count.</p>
     */
    @Override
    public void onRunRequested(int games, String seedText, RuleVariant variant, int threadCount,
                               int playerCount, boolean compareVariants) {
        if (worker != null && !worker.isDone()) {
            return;
        }

        long seed;
        try {
            seed = parseSeed(seedText);
        } catch (NumberFormatException ex) {
            view.showError("La llavor ha de ser un enter de 64 bits.");
            return;
        }

        view.setRunning(true);
        worker = new SwingWorker<>() {
            @Override
            protected SimulationResult doInBackground() {
                long start = System.nanoTime();
                SimulationData data = Simulation.runBatch(games, seed, variant, threadCount, playerCount);
                int[] observations = data.turnCounts();
                SimulationStats stats = SimulationStats.from(observations);
                Map<RuleVariant, SimulationStats> comparisonStats = compareVariants
                        ? compareVariants(games, seed, variant, threadCount, playerCount, stats)
                        : Map.of();
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                return new SimulationResult(stats, observations, data.squareVisits(), data.winnerCounts(), elapsedMillis, seed,
                        variant, threadCount, comparisonStats);
            }

            @Override
            protected void done() {
                try {
                    SimulationResult result = get();
                    view.setRunning(false);
                    view.showResult(result);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    view.setRunning(false);
                    view.showError("Simulacio interrompuda.");
                } catch (ExecutionException ex) {
                    view.setRunning(false);
                    view.showError("Error de simulacio: " + rootMessage(ex));
                }
            }
        };
        worker.execute();
    }

    /**
     * Builds the optional comparison table for all known rule variants.
     */
    private static Map<RuleVariant, SimulationStats> compareVariants(int games, long seed, RuleVariant selected,
                                                                     int threadCount, int playerCount,
                                                                     SimulationStats selectedStats) {
        Map<RuleVariant, SimulationStats> comparison = new LinkedHashMap<>();
        for (RuleVariant variant : RuleVariant.values()) {
            if (variant == selected) {
                comparison.put(variant, selectedStats);
            } else {
                SimulationData data = Simulation.runBatch(games, seed, variant, threadCount, playerCount);
                comparison.put(variant, SimulationStats.from(data.turnCounts()));
            }
        }
        return comparison;
    }

    /**
     * Parses a user-provided seed, or creates a fresh one when the field is blank.
     */
    private static long parseSeed(String seedText) {
        if (seedText == null || seedText.isBlank()) {
            return System.nanoTime();
        }
        return Long.parseLong(seedText.trim());
    }

    /**
     * Extracts the most useful exception message for display in the GUI status.
     */
    private static String rootMessage(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause == null || cause.getMessage() == null) {
            return ex.getMessage();
        }
        return cause.getMessage();
    }
}
