package com.serafinebot.p2.controller;

import com.serafinebot.p2.model.Board;
import com.serafinebot.p2.model.PieceType;
import com.serafinebot.p2.model.Position;
import com.serafinebot.p2.model.SolverMetrics;
import com.serafinebot.p2.view.ConfigPanel;
import com.serafinebot.p2.view.MainFrame;

import javax.swing.Timer;
import java.util.List;

/**
 * Main controller that orchestrates the solving process and solution navigation.
 * <p>
 * Owns all navigation state (solution path, piece types, current step) so that
 * {@link MainFrame} remains a pure layout/delegation class with no domain logic.
 * The Swing {@link Timer} used for animation lives here too; it calls
 * {@link #stepForward()} on each tick.
 */
public class SolverController {

    private final MainFrame view;
    private SolverTask currentTask;

    // ---- Navigation state ----
    private List<Position> currentSolution;
    private PieceType whiteType;
    private PieceType blackType;
    private int currentStep;

    // ---- Animation ----
    private Timer animationTimer;

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
     * Register action listeners on all control panel buttons, navigation
     * buttons, the animation speed slider, and the configuration-change callback.
     */
    private void wireListeners() {
        // Execution buttons
        view.getControlPanel().addStartListener(e -> startSolving());
        view.getControlPanel().addStopListener(e -> stopSolving());
        view.getControlPanel().addResetListener(e -> resetAll());

        // Navigation buttons
        view.getControlPanel().addPrevListener(e -> stepBack());
        view.getControlPanel().addNextListener(e -> stepForward());
        view.getControlPanel().addPlayListener(e -> toggleAnimation());

        // Animation speed slider
        view.getControlPanel().addSpeedChangeListener(e -> {
            if (animationTimer != null && animationTimer.isRunning()) {
                animationTimer.setDelay(view.getControlPanel().getAnimationDelay());
            }
        });

        // Config change: resize board + refresh piece preview.
        view.getConfigPanel().setOnConfigChanged(() -> {
            view.resizeBoard(
                view.getConfigPanel().getBoardRows(),
                view.getConfigPanel().getBoardCols()
            );
            view.refreshPiecePreview();
        });
    }

    // =====================================================================
    //  Solver actions
    // =====================================================================

    /**
     * Read the current configuration, create a solver and launch it
     * on a background thread.
     */
    private void startSolving() {
        ConfigPanel config = view.getConfigPanel();

        if (!config.validateConfig()) {
            return;
        }

        int rows = config.getBoardRows();
        int cols = config.getBoardCols();
        PieceType wType = config.getWhiteType();
        PieceType bType = config.getBlackType();
        Position whiteStart = new Position(config.getWhiteRow(), config.getWhiteCol());
        Position blackStart = new Position(config.getBlackRow(), config.getBlackCol());

        clearSolution();
        view.getBoardPanel().reset();
        view.getStatsPanel().resetDisplay();
        view.setExecutingState();

        Board board = new Board(rows, cols);
        BacktrackingSolver solver = new BacktrackingSolver(
            board, wType, whiteStart, bType, blackStart
        );

        currentTask = new SolverTask(
            solver,
            () -> view.updateStats(solver.getMetrics()),
            (found, metrics) -> onSolvingComplete(found, solver)
        );
        currentTask.execute();
    }

    /** Request graceful termination of the running solver. */
    private void stopSolving() {
        if (currentTask != null) {
            currentTask.stopSolver();
        }
    }

    /** Stop any running solver, clear the board and reset statistics. */
    private void resetAll() {
        stopSolving();
        stopAnimation();
        clearSolution();
        view.getBoardPanel().reset();
        view.getStatsPanel().resetDisplay();
        view.setIdleState(false);
        view.setSolverState("waiting");
        view.refreshPiecePreview();
    }

    // =====================================================================
    //  Navigation
    // =====================================================================

    /** Move one step back and refresh the board. */
    private void stepBack() {
        if (currentSolution == null || currentSolution.isEmpty()) return;
        if (currentStep > 0) {
            currentStep--;
            renderCurrentStep();
        }
    }

    /** Move one step forward and refresh the board. */
    private void stepForward() {
        if (currentSolution == null || currentSolution.isEmpty()) return;
        if (currentStep < currentSolution.size() - 1) {
            currentStep++;
            renderCurrentStep();
        } else {
            stopAnimation();
        }
    }

    /** Toggle animation play/pause. */
    private void toggleAnimation() {
        if (view.getControlPanel().isPlaying()) {
            stopAnimation();
        } else {
            startAnimation();
        }
    }

    private void startAnimation() {
        if (currentSolution == null || currentSolution.isEmpty()) return;
        if (currentStep >= currentSolution.size() - 1) {
            currentStep = 0;
        }
        view.getControlPanel().setPlayLabel(true);
        animationTimer = new Timer(view.getControlPanel().getAnimationDelay(), e -> stepForward());
        animationTimer.start();
    }

    /** Stop the animation timer if running. */
    public void stopAnimation() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        view.getControlPanel().setPlayLabel(false);
    }

    /** Render the board at {@link #currentStep}. */
    private void renderCurrentStep() {
        view.getBoardPanel().updateBoard(currentSolution, currentStep, whiteType, blackType);
    }

    // =====================================================================
    //  Solution management
    // =====================================================================

    /**
     * Load a completed solution into the controller and display it in full.
     *
     * @param path      Hamiltonian path
     * @param whiteType White piece type
     * @param blackType Black piece type
     */
    private void loadSolution(List<Position> path, PieceType whiteType, PieceType blackType) {
        this.currentSolution = path;
        this.whiteType = whiteType;
        this.blackType = blackType;
        this.currentStep = path.size() - 1;
        view.getBoardPanel().showFullSolution(path, whiteType, blackType);
    }

    /** Clear the current solution and reset navigation state. */
    private void clearSolution() {
        stopAnimation();
        currentSolution = null;
        whiteType = null;
        blackType = null;
        currentStep = 0;
    }

    // =====================================================================
    //  Completion handler
    // =====================================================================

    /**
     * Called on the EDT when the solver finishes (success, failure or stop).
     */
    private void onSolvingComplete(boolean found, BacktrackingSolver solver) {
        view.updateStats(solver.getMetrics());

        if (solver.isStopped()) {
            view.setSolverState("stopped");
            view.setIdleState(false);
        } else if (found) {
            view.setSolverState("found");
            loadSolution(solver.getSolution(), solver.getWhiteType(), solver.getBlackType());
            view.setIdleState(true);
        } else {
            view.setSolverState("not_found");
            view.setIdleState(false);
        }

        currentTask = null;
    }

}
