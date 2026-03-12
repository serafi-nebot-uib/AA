package com.serafinebot.p2.view;

import com.serafinebot.p2.model.PieceType;
import com.serafinebot.p2.model.Position;
import com.serafinebot.p2.model.SolverMetrics;

import java.util.List;

import javax.swing.*;
import java.awt.*;

/**
 * Main application window.
 * Assembles ConfigPanel (WEST), BoardPanel (CENTER),
 * ControlPanel + StatsPanel (SOUTH) into a BorderLayout.
 * <p>
 * Provides delegation methods so the controller can interact with the
 * view layer through a single entry point.
 */
public class MainFrame extends JFrame {

    private final ConfigPanel configPanel;
    private final BoardPanel boardPanel;
    private final ControlPanel controlPanel;
    private final StatsPanel statsPanel;

    // Step-by-step navigation state
    private List<Position> currentSolution;
    private PieceType piece1Type;
    private PieceType piece2Type;
    private int currentStep = 0;

    // Animation timer
    private Timer animationTimer;

    /**
     * Create and layout the main window.
     */
    public MainFrame() {
        super("P2 - Recorregut Hamiltonià amb Backtracking");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 800);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);

        // Create components
        configPanel = new ConfigPanel();
        boardPanel = new BoardPanel();
        controlPanel = new ControlPanel();
        statsPanel = new StatsPanel();

        // Layout
        setLayout(new BorderLayout(6, 6));

        // WEST: config in a scroll pane (can be tall)
        JScrollPane configScroll = new JScrollPane(configPanel);
        configScroll.setPreferredSize(new Dimension(300, 0));
        configScroll.setMinimumSize(new Dimension(300, 200));
        configScroll.setBorder(BorderFactory.createTitledBorder("Configuració"));
        configScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        configScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        configScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(configScroll, BorderLayout.WEST);

        // CENTER: board in a scroll pane
        JScrollPane boardScroll = new JScrollPane(boardPanel);
        boardScroll.setBorder(BorderFactory.createTitledBorder("Tauler"));
        add(boardScroll, BorderLayout.CENTER);

        // SOUTH: controls + stats
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(controlPanel, BorderLayout.CENTER);
        southPanel.add(statsPanel, BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);

        // Wire internal navigation buttons
        wireNavigationButtons();

        // Wire cell click to placement mode
        boardPanel.setOnCellClicked((row, col) -> handleCellClick(row, col));

        // Wire config panel changes to board size updates + piece preview
        configPanel.setOnConfigChanged(() -> {
            boardPanel.setBoard(configPanel.getBoardRows(), configPanel.getBoardCols());
            refreshPiecePreview();
        });

        // Trigger initial board setup + preview
        boardPanel.setBoard(configPanel.getBoardRows(), configPanel.getBoardCols());
        refreshPiecePreview();
    }

    // =====================================================================
    //  Delegation: Config
    // =====================================================================

    /** @return The configuration panel. */
    public ConfigPanel getConfigPanel() {
        return configPanel;
    }

    /** @return The control panel. */
    public ControlPanel getControlPanel() {
        return controlPanel;
    }

    /** @return The stats panel. */
    public StatsPanel getStatsPanel() {
        return statsPanel;
    }

    /** @return The board panel. */
    public BoardPanel getBoardPanel() {
        return boardPanel;
    }

    // =====================================================================
    //  Delegation: Board operations
    // =====================================================================

    /**
     * Show the complete solution on the board.
     *
     * @param path       Hamiltonian path solution
     * @param piece1Type Piece 1 type
     * @param piece2Type Piece 2 type
     */
    public void showSolution(List<Position> path,
                             PieceType piece1Type, PieceType piece2Type) {
        this.currentSolution = path;
        this.piece1Type = piece1Type;
        this.piece2Type = piece2Type;
        this.currentStep = (path != null) ? path.size() - 1 : 0;

        if (path != null && !path.isEmpty()) {
            boardPanel.showFullSolution(path, piece1Type, piece2Type);
        }
    }

    /**
     * Reset the board and navigation state.
     */
    public void resetBoard() {
        stopAnimation();
        currentSolution = null;
        currentStep = 0;
        boardPanel.reset();
    }

    /**
     * Update the board to show a specific board configuration
     * (used for resizing from config changes).
     */
    public void resizeBoard(int rows, int cols) {
        boardPanel.setBoard(rows, cols);
    }

    /**
     * Handle a cell click on the board: if a placement mode is active,
     * set the corresponding piece's position and refresh the preview.
     *
     * @param row Clicked row (0-based)
     * @param col Clicked column (0-based)
     */
    private void handleCellClick(int row, int col) {
        int placement = configPanel.getActivePlacement();
        if (placement == 0) return; // No placement mode active

        if (placement == 1) {
            configPanel.setPiece1Position(row, col);
        } else {
            configPanel.setPiece2Position(row, col);
        }

        // Deactivate placement mode after placing
        configPanel.clearPlacementMode();

        // refreshPiecePreview() is called automatically via the spinner
        // change listener → fireConfigChanged → onConfigChanged callback
    }

    /**
     * Show piece icons on the board at their configured starting positions.
     * Only shown when no solution is displayed (idle/preview state).
     */
    public void refreshPiecePreview() {
        if (currentSolution != null) return; // Don't overwrite solution display
        boardPanel.showPiecePreview(
            configPanel.getPiece1Type(), configPanel.getPiece1Row(), configPanel.getPiece1Col(),
            configPanel.getPiece2Type(), configPanel.getPiece2Row(), configPanel.getPiece2Col()
        );
    }

    // =====================================================================
    //  Delegation: Stats
    // =====================================================================

    /**
     * Update statistics display with current metrics.
     *
     * @param metrics Current solver metrics
     */
    public void updateStats(SolverMetrics metrics) {
        statsPanel.updateMetrics(metrics);
    }

    /**
     * Set the solver state indicator.
     *
     * @param state One of "waiting", "searching", "found", "not_found", "stopped"
     */
    public void setSolverState(String state) {
        statsPanel.setState(state);
    }

    // =====================================================================
    //  Delegation: Controls
    // =====================================================================

    /**
     * Set UI to "executing" mode (disable config, show stop button, etc.).
     */
    public void setExecutingState() {
        controlPanel.setExecutionState();
        configPanel.setConfigEnabled(false);
        statsPanel.setState("searching");
        statsPanel.startLiveTimer();
    }

    /**
     * Set UI to "idle" mode with optional solution available.
     *
     * @param hasSolution true if a solution is available for navigation
     */
    public void setIdleState(boolean hasSolution) {
        controlPanel.setSolutionState(hasSolution);
        configPanel.setConfigEnabled(true);
        statsPanel.stopLiveTimer();
    }

    // =====================================================================
    //  Step-by-step navigation (internal wiring)
    // =====================================================================

    /**
     * Wire <<, >>, Play buttons to internal navigation logic.
     */
    private void wireNavigationButtons() {
        controlPanel.addPrevListener(e -> navigatePrev());
        controlPanel.addNextListener(e -> navigateNext());
        controlPanel.addPlayListener(e -> toggleAnimation());

        // Update running animation speed when slider changes
        controlPanel.addSpeedChangeListener(e -> {
            if (animationTimer != null && animationTimer.isRunning()) {
                animationTimer.setDelay(controlPanel.getAnimationDelay());
            }
        });
    }

    private void navigatePrev() {
        if (currentSolution == null || currentSolution.isEmpty()) return;
        if (currentStep > 0) {
            currentStep--;
            boardPanel.updateBoard(currentSolution, currentStep, piece1Type, piece2Type);
        }
    }

    private void navigateNext() {
        if (currentSolution == null || currentSolution.isEmpty()) return;
        if (currentStep < currentSolution.size() - 1) {
            currentStep++;
            boardPanel.updateBoard(currentSolution, currentStep, piece1Type, piece2Type);
        }
    }

    private void toggleAnimation() {
        if (controlPanel.isPlaying()) {
            stopAnimation();
        } else {
            startAnimation();
        }
    }

    private void startAnimation() {
        if (currentSolution == null || currentSolution.isEmpty()) return;

        // Reset to beginning if at the end
        if (currentStep >= currentSolution.size() - 1) {
            currentStep = 0;
        }

        controlPanel.setPlayLabel(true);

        animationTimer = new Timer(controlPanel.getAnimationDelay(), e -> {
            if (currentStep < currentSolution.size() - 1) {
                currentStep++;
                boardPanel.updateBoard(currentSolution, currentStep, piece1Type, piece2Type);
            } else {
                // Reached the end
                stopAnimation();
            }
        });
        animationTimer.start();
    }

    /**
     * Stop the animation timer if running.
     */
    public void stopAnimation() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        controlPanel.setPlayLabel(false);
    }
}
