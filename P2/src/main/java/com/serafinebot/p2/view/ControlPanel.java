package com.serafinebot.p2.view;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Hashtable;

/**
 * Panel containing action buttons for controlling the solver.
 * Buttons: Iniciar, Aturar, Reiniciar, <<, >>, Reproduir/Pausar.
 * Includes a speed slider for animation playback.
 * <p>
 * All labels are in Catalan.
 */
public class ControlPanel extends JPanel {

    private final JButton startButton;
    private final JButton stopButton;
    private final JButton resetButton;
    private final JButton prevButton;
    private final JButton nextButton;
    private final JButton playButton;
    private final JSlider speedSlider;

    /** Slider range: min delay (fast) to max delay (slow), in ms. */
    private static final int MIN_DELAY = 50;
    private static final int MAX_DELAY = 2000;
    private static final int DEFAULT_DELAY = 500;

    private boolean isPlaying = false;

    /**
     * Create the control panel with all buttons and speed slider.
     */
    public ControlPanel() {
        setLayout(new BorderLayout(0, 2));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // --- Button x ---
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));

        // Execution controls
        startButton = createButton("\u25B6 Iniciar", "Inicia la cerca del recorregut hamiltonià");
        stopButton = createButton("\u25A0 Aturar", "Atura la cerca en curs");
        resetButton = createButton("\u21BA Reiniciar", "Reinicia el tauler i la configuració");

        // Navigation controls
        prevButton = createButton("\u25C0\u25C0", "Mostra el pas anterior");
        nextButton = createButton("\u25B6\u25B6", "Mostra el pas següent");
        playButton = createButton("\u25B6 Reproduir", "Reprodueix l'animaci\u00f3 autom\u00e0tica");

        buttonRow.add(startButton);
        buttonRow.add(stopButton);
        buttonRow.add(resetButton);
        buttonRow.add(new JSeparator(SwingConstants.VERTICAL) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(2, 28);
            }
        });
        buttonRow.add(prevButton);
        buttonRow.add(nextButton);
        buttonRow.add(playButton);

        add(buttonRow, BorderLayout.CENTER);

        // --- Speed slider x ---
        // Slider value = delay in ms; inverted so right = fast
        speedSlider = new JSlider(MIN_DELAY, MAX_DELAY, DEFAULT_DELAY);
        speedSlider.setInverted(true); // right side = low delay = fast
        speedSlider.setToolTipText("Velocitat de l'animaci\u00f3");
        speedSlider.setPreferredSize(new Dimension(250, 40));

        Hashtable<Integer, JLabel> labels = new Hashtable<>();
        labels.put(MAX_DELAY, new JLabel("Lent"));
        labels.put(MIN_DELAY, new JLabel("R\u00e0pid"));
        speedSlider.setLabelTable(labels);
        speedSlider.setPaintLabels(true);

        JPanel sliderRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        sliderRow.add(new JLabel("Velocitat:"));
        sliderRow.add(speedSlider);

        add(sliderRow, BorderLayout.SOUTH);

        // Initial state: ready to start, navigation disabled
        setIdleState();
    }

    // =====================================================================
    //  State management
    // =====================================================================

    /**
     * Set controls for idle state (nothing running, no solution).
     */
    public void setIdleState() {
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        resetButton.setEnabled(true);
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        playButton.setEnabled(false);
        setPlayLabel(false);
    }

    /**
     * Set controls for execution state (solver running).
     */
    public void setExecutionState() {
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        resetButton.setEnabled(false);
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        playButton.setEnabled(false);
        setPlayLabel(false);
    }

    /**
     * Set controls for solution-available state.
     *
     * @param hasSolution true if a solution exists for step navigation
     */
    public void setSolutionState(boolean hasSolution) {
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        resetButton.setEnabled(true);
        prevButton.setEnabled(hasSolution);
        nextButton.setEnabled(hasSolution);
        playButton.setEnabled(hasSolution);
        if (!hasSolution) {
            setPlayLabel(false);
        }
    }

    /**
     * Toggle play/pause label.
     *
     * @param playing true → show "Pausar", false → show "Reproduir"
     */
    public void setPlayLabel(boolean playing) {
        this.isPlaying = playing;
        playButton.setText(playing ? "\u2759\u2759 Pausar" : "\u25B6 Reproduir");
    }

    /** @return Whether the play button is currently in "playing" mode. */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * @return Current animation delay in milliseconds (from the speed slider).
     */
    public int getAnimationDelay() {
        return speedSlider.getValue();
    }

    // =====================================================================
    //  Listener registration
    // =====================================================================

    public void addStartListener(ActionListener l) {
        startButton.addActionListener(l);
    }

    public void addStopListener(ActionListener l) {
        stopButton.addActionListener(l);
    }

    public void addResetListener(ActionListener l) {
        resetButton.addActionListener(l);
    }

    public void addPrevListener(ActionListener l) {
        prevButton.addActionListener(l);
    }

    public void addNextListener(ActionListener l) {
        nextButton.addActionListener(l);
    }

    public void addPlayListener(ActionListener l) {
        playButton.addActionListener(l);
    }

    public void addSpeedChangeListener(ChangeListener l) {
        speedSlider.addChangeListener(l);
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private JButton createButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        return button;
    }
}
