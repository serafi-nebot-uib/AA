package com.serafinebot.p2.controller;

import com.serafinebot.p2.model.SolverMetrics;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

/**
 * Background task that runs the {@link BacktrackingSolver} on a worker thread.
 * <p>
 * Uses {@link SwingWorker} to keep the GUI responsive.
 * Progress updates and the completion callback are delivered on the EDT.
 */
public class SolverTask extends SwingWorker<Boolean, Void> {

    private final BacktrackingSolver solver;
    private final Runnable onProgress;
    private final BiConsumer<Boolean, SolverMetrics> onComplete;

    /**
     * Create a new solver task.
     *
     * @param solver     The fully configured solver to run
     * @param onProgress Called on the EDT after each progress update
     *                   (every {@link BacktrackingSolver#PROGRESS_UPDATE_INTERVAL} iterations)
     * @param onComplete Called on the EDT when solving finishes;
     *                   receives (solutionFound, finalMetrics)
     */
    public SolverTask(BacktrackingSolver solver,
                      Runnable onProgress,
                      BiConsumer<Boolean, SolverMetrics> onComplete) {
        this.solver = solver;
        this.onProgress = onProgress;
        this.onComplete = onComplete;
    }

    /**
     * Run the solver on a background thread.
     * Wires the solver's progress callback to deliver EDT-safe updates.
     *
     * @return true if a Hamiltonian path was found
     */
    @Override
    protected Boolean doInBackground() {
        // Wire solver progress to EDT updates
        solver.setProgressCallback(visited ->
            SwingUtilities.invokeLater(onProgress)
        );
        return solver.solve();
    }

    /**
     * Called on the EDT when {@link #doInBackground()} completes.
     * Delivers the result and final metrics to the completion listener.
     */
    @Override
    protected void done() {
        try {
            boolean found = get();
            onComplete.accept(found, solver.getMetrics());
        } catch (CancellationException e) {
            // Task was cancelled via stop button
            onComplete.accept(false, solver.getMetrics());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // Restore interrupted status
            onComplete.accept(false, solver.getMetrics());
        } catch (ExecutionException e) {
            onComplete.accept(false, solver.getMetrics());
        }
    }

    /**
     * Stop the solver gracefully and cancel this task.
     * Sets the solver's internal stop flag so the backtracking loop
     * exits at the next iteration check.
     */
    public void stopSolver() {
        solver.stop();
        cancel(false);  // Don't interrupt; let the stop flag handle termination
    }

    /** @return The underlying solver instance. */
    public BacktrackingSolver getSolver() {
        return solver;
    }
}
