package com.serafinebot.p2.view;

import com.serafinebot.p2.model.SolverMetrics;

import javax.swing.*;
import java.awt.*;

/**
 * Panel that displays real-time solving statistics.
 * Shows elapsed time, iteration count, backtrack count,
 * maximum depth and solver state.
 * <p>
 * Includes a Swing Timer for live elapsed-time updates while the solver runs.
 * All labels are in Catalan.
 */
public class StatsPanel extends JPanel {

    // ---- State colours ----
    private static final Color COLOR_WAITING = Color.GRAY;
    private static final Color COLOR_SEARCHING = new Color(0, 100, 200);
    private static final Color COLOR_FOUND = new Color(0, 150, 0);
    private static final Color COLOR_NOT_FOUND = new Color(200, 0, 0);
    private static final Color COLOR_STOPPED = new Color(180, 120, 0);

    // ---- Value labels ----
    private final JLabel timeValue;
    private final JLabel iterationsValue;
    private final JLabel backtracksValue;
    private final JLabel maxDepthValue;
    private final JLabel stateValue;

    // ---- Live timer ----
    private final Timer liveTimer;
    private long timerStartMillis;

    /**
     * Create the statistics panel.
     */
    public StatsPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Estadístiques"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        timeValue = addRow(gbc, row++, "Temps transcorregut:");
        iterationsValue = addRow(gbc, row++, "Iteracions:");
        backtracksValue = addRow(gbc, row++, "Backtracks:");
        maxDepthValue = addRow(gbc, row++, "Profunditat màxima:");
        stateValue = addRow(gbc, row, "Estat:");

        // Live timer: updates the elapsed time label every second
        liveTimer = new Timer(1000, e -> {
            long elapsed = System.currentTimeMillis() - timerStartMillis;
            timeValue.setText(formatTime(elapsed));
        });

        // Initial display
        resetDisplay();
    }

    // =====================================================================
    //  Public update methods
    // =====================================================================

    /**
     * Update the elapsed time display.
     *
     * @param millis Elapsed time in milliseconds
     */
    public void updateTime(long millis) {
        timeValue.setText(formatTime(millis));
    }

    /**
     * Update all metric fields from a SolverMetrics snapshot.
     *
     * @param metrics Current metrics
     */
    public void updateMetrics(SolverMetrics metrics) {
        if (metrics == null) return;
        updateTime(metrics.getElapsedTimeMillis());
        iterationsValue.setText(formatNumber(metrics.getIterations()));
        backtracksValue.setText(formatNumber(metrics.getBacktracks()));
        maxDepthValue.setText(String.valueOf(metrics.getMaxDepth()));
    }

    /**
     * Set the solver state text with an appropriate colour.
     *
     * @param state State identifier: "waiting", "searching", "found", "not_found", "stopped"
     */
    public void setState(String state) {
        switch (state) {
            case "waiting" -> {
                stateValue.setText("Esperant");
                stateValue.setForeground(COLOR_WAITING);
            }
            case "searching" -> {
                stateValue.setText("Buscant...");
                stateValue.setForeground(COLOR_SEARCHING);
            }
            case "found" -> {
                stateValue.setText("Solució trobada!");
                stateValue.setForeground(COLOR_FOUND);
            }
            case "not_found" -> {
                stateValue.setText("No hi ha solució");
                stateValue.setForeground(COLOR_NOT_FOUND);
            }
            case "stopped" -> {
                stateValue.setText("Aturat");
                stateValue.setForeground(COLOR_STOPPED);
            }
            default -> {
                stateValue.setText(state);
                stateValue.setForeground(COLOR_WAITING);
            }
        }
    }

    /**
     * Start the live timer that updates elapsed time every second.
     */
    public void startLiveTimer() {
        timerStartMillis = System.currentTimeMillis();
        liveTimer.start();
    }

    /**
     * Stop the live timer.
     */
    public void stopLiveTimer() {
        liveTimer.stop();
    }

    /**
     * Reset all displayed values to defaults.
     */
    public void resetDisplay() {
        timeValue.setText("00:00");
        iterationsValue.setText("0");
        backtracksValue.setText("0");
        maxDepthValue.setText("0");
        setState("waiting");
        stopLiveTimer();
    }

    // =====================================================================
    //  Internal helpers
    // =====================================================================

    /**
     * Format milliseconds into MM:SS or HH:MM:SS.
     */
    private String formatTime(long millis) {
        long totalSec = millis / 1000;
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Format a large number with locale-aware thousands separators.
     */
    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Add a label-value row and return the value label.
     */
    private JLabel addRow(GridBagConstraints gbc, int row, String labelText) {
        gbc.gridy = row;

        gbc.gridx = 0;
        gbc.weightx = 0;
        JLabel label = new JLabel(labelText);
        add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JLabel value = new JLabel("--");
        value.setFont(value.getFont().deriveFont(Font.BOLD));
        add(value, gbc);

        gbc.weightx = 0;
        return value;
    }
}
