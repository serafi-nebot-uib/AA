package com.serafinebot.p2.controller;

import com.serafinebot.p2.model.BacktrackingSolver;
import com.serafinebot.p2.model.Board;
import com.serafinebot.p2.model.PieceType;
import com.serafinebot.p2.model.Position;
import com.serafinebot.p2.model.SolverMetrics;
import com.serafinebot.p2.view.ConfigPanel;
import com.serafinebot.p2.view.MainFrame;

/**
 * Main controller that orchestrates the solving process.
 * <p>
 * Bridges the model ({@link BacktrackingSolver}) with the view
 * ({@link MainFrame}), wiring user actions (Start/Stop/Reset) to
 * the appropriate model operations and feeding results back to the GUI.
 * <p>
 * Also manages time prediction updates whenever the configuration changes.
 */
public class SolverController {

    private final MainFrame view;
    private SolverTask currentTask;

    /**
     * Create the controller and wire it to the given view.
     * All button listeners and configuration-change callbacks are
     * registered during construction.
     *
     * @param view The main application frame
     */
    public SolverController(MainFrame view) {
        this.view = view;
        wireListeners();
    }

    // =====================================================================
    //  Listener wiring
    // =====================================================================

    /**
     * Register action listeners on the control panel buttons and
     * the configuration-change callback.
     */
    private void wireListeners() {
        // Execution buttons
        view.getControlPanel().addStartListener(e -> startSolving());
        view.getControlPanel().addStopListener(e -> stopSolving());
        view.getControlPanel().addResetListener(e -> resetAll());

        // Config change: resize board + refresh piece preview.
        // This intentionally replaces the callback set by MainFrame's
        // constructor, because the controller is the authoritative owner
        // of the config-change flow once it is created.
        view.getConfigPanel().setOnConfigChanged(() -> {
            view.resizeBoard(
                view.getConfigPanel().getBoardRows(),
                view.getConfigPanel().getBoardCols()
            );
            view.refreshPiecePreview();
        });
    }

    // =====================================================================
    //  Actions
    // =====================================================================

    /**
     * Read the current configuration, create a solver and launch it
     * on a background thread.
     */
    private void startSolving() {
        ConfigPanel config = view.getConfigPanel();

        // Validate first
        if (!config.validateConfig()) {
            return;
        }

        // Read parameters
        int rows = config.getBoardRows();
        int cols = config.getBoardCols();
        PieceType piece1Type = config.getPiece1Type();
        PieceType piece2Type = config.getPiece2Type();
        Position piece1Start = new Position(config.getPiece1Row(), config.getPiece1Col());
        Position piece2Start = new Position(config.getPiece2Row(), config.getPiece2Col());

        // Prepare the view
        view.resetBoard();
        view.getStatsPanel().resetDisplay();
        view.setExecutingState();

        // Build model objects
        Board board = new Board(rows, cols);
        BacktrackingSolver solver = new BacktrackingSolver(
            board, piece1Type, piece1Start, piece2Type, piece2Start
        );

        // Create and execute background task
        currentTask = new SolverTask(
            solver,
            () -> view.updateStats(solver.getMetrics()),              // progress (EDT)
            (found, metrics) -> onSolvingComplete(found, solver)      // completion (EDT)
        );
        currentTask.execute();
    }

    /**
     * Request graceful termination of the running solver.
     */
    private void stopSolving() {
        if (currentTask != null) {
            currentTask.stopSolver();
        }
    }

    /**
     * Stop any running solver, clear the board and reset statistics.
     */
    private void resetAll() {
        stopSolving();
        view.resetBoard();
        view.getStatsPanel().resetDisplay();
        view.setIdleState(false);
        view.setSolverState("waiting");
        view.refreshPiecePreview();
    }

    // =====================================================================
    //  Completion handler
    // =====================================================================

    /**
     * Called on the EDT when the solver finishes (success, failure or stop).
     *
     * @param found  true if a Hamiltonian path was found
     * @param solver The solver that just finished
     */
    private void onSolvingComplete(boolean found, BacktrackingSolver solver) {
        // Always push final metrics
        view.updateStats(solver.getMetrics());

        if (solver.isStopped()) {
            view.setSolverState("stopped");
            view.setIdleState(false);
        } else if (found) {
            view.setSolverState("found");
            view.showSolution(solver.getSolution(),
                              solver.getPiece1Type(), solver.getPiece2Type());
            view.setIdleState(true);
        } else {
            view.setSolverState("not_found");
            view.setIdleState(false);
        }

        currentTask = null;
    }

}
