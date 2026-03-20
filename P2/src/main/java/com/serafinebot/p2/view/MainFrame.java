package com.serafinebot.p2.view;

import com.serafinebot.p2.model.SolverMetrics;

import javax.swing.*;
import java.awt.*;

/**
 * Main application window.
 * Assembles ConfigPanel (WEST), BoardPanel (CENTER),
 * ControlPanel + StatsPanel (SOUTH) into a BorderLayout.
 * <p>
 * Pure layout and delegation class — holds no navigation or domain state.
 * All such state lives in {@link com.serafinebot.p2.controller.SolverController}.
 */
public class MainFrame extends JFrame {

    private static final int WINDOW_WIDTH        = 1100;
    private static final int WINDOW_HEIGHT       = 800;
    private static final int WINDOW_MIN_WIDTH    = 800;
    private static final int WINDOW_MIN_HEIGHT   = 600;
    private static final int CONFIG_PANEL_WIDTH  = 300;
    private static final int CONFIG_PANEL_MIN_H  = 200;

    private final ConfigPanel configPanel;
    private final BoardPanel boardPanel;
    private final ControlPanel controlPanel;
    private final StatsPanel statsPanel;

    /**
     * Create and layout the main window.
     */
    public MainFrame() {
        super("P2 - Recorregut Hamiltonià amb Backtracking");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setMinimumSize(new Dimension(WINDOW_MIN_WIDTH, WINDOW_MIN_HEIGHT));
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
        configScroll.setPreferredSize(new Dimension(CONFIG_PANEL_WIDTH, 0));
        configScroll.setMinimumSize(new Dimension(CONFIG_PANEL_WIDTH, CONFIG_PANEL_MIN_H));
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

        // Wire cell click to placement mode
        boardPanel.setOnCellClicked((row, col) -> handleCellClick(row, col));

        // Trigger initial board setup + preview
        // Note: config-change wiring is owned by SolverController, not here.
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
     * Reset the board panel to its empty state.
     */
    public void resetBoard() {
        boardPanel.reset();
    }

    /**
     * Resize the board grid.
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
        if (placement == 0) return;

        if (placement == 1) {
            configPanel.setWhitePosition(row, col);
        } else {
            configPanel.setBlackPosition(row, col);
        }

        configPanel.clearPlacementMode();
        // refreshPiecePreview() fires automatically via spinner → fireConfigChanged → onConfigChanged
    }

    /**
     * Show piece icons on the board at their configured starting positions.
     */
    public void refreshPiecePreview() {
        boardPanel.showPiecePreview(
            configPanel.getWhiteType(), configPanel.getWhiteRow(), configPanel.getWhiteCol(),
            configPanel.getBlackType(), configPanel.getBlackRow(), configPanel.getBlackCol()
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

}
