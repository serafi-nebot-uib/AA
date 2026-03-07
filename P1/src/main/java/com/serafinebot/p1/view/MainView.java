package com.serafinebot.p1.view;

import com.serafinebot.p1.model.AlgorithmType;
import com.serafinebot.p1.model.Model;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Finestra principal de l'aplicacio.
 * No exposa cap sub-panell ni element visual. Tota comunicacio
 * amb el controlador es fa a traves de {@link ViewListener} i
 * metodes que operen amb dades, mai amb components Swing.
 */
public class MainView extends JFrame {

    private final ControlPanel controlPanel;
    private final GraphPanel graphPanel;
    private final ResultsPanel resultsPanel;

    public MainView(Model model) {
        super("Analisi de Costos Computacionals Asimptotics");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        controlPanel = new ControlPanel();
        graphPanel = new GraphPanel(model);
        resultsPanel = new ResultsPanel(model);

        add(controlPanel, BorderLayout.NORTH);
        add(graphPanel, BorderLayout.CENTER);
        add(resultsPanel, BorderLayout.EAST);

        controlPanel.addVisibilityListener(graphPanel::setAlgorithmVisible);

        // Escoltar canvis del model per actualitzar la vista
        model.addListener(() -> {
            graphPanel.repaint();
            resultsPanel.updateResults();
        });

        setSize(1250, 780);
        setMinimumSize(new Dimension(950, 600));
        setLocationRelativeTo(null);
    }

    /** Registra un listener d'events de la vista. */
    public void addViewListener(ViewListener listener) {
        controlPanel.addViewListener(listener);
    }

    /** Actualitza l'estat visual d'un algorisme (dada, no component). */
    public void setAlgorithmRunning(AlgorithmType type, boolean running) {
        controlPanel.setAlgorithmRunning(type, running);
    }

    /** Canvia l'escala de la grafica. */
    public void setLogScale(boolean logScale) {
        graphPanel.setLogScale(logScale);
    }

    /** Mostra les previsions a la vista de resultats. */
    public void showPredictions(long predictN, Map<AlgorithmType, Double> predictions) {
        resultsPanel.showPredictions(predictN, predictions);
    }
}
