package com.serafinebot.p3.view;

import com.serafinebot.p3.model.Model;
import com.serafinebot.p3.model.PointCloud;
import com.serafinebot.p3.model.PointPair;

import javax.swing.*;
import java.awt.*;

public class MainView extends JFrame {

    public static final double RANGE_MIN = 0;
    public static final double RANGE_MAX = 1000;

    private final Model model;
    private ViewListener listener;

    // Controls (private — not exposed)
    private final JSpinner nSpinner;
    private final JComboBox<PointCloud.Distribution> distributionCombo;
    private final JButton generateButton;
    private final JButton runButton;
    private final JButton benchmarkButton;

    // Results labels (private)
    private final JLabel closestBruteLabel;
    private final JLabel closestDCLabel;
    private final JLabel farthestLabel;
    private final JLabel timeBruteLabel;
    private final JLabel timeDCLabel;
    private final JLabel timeFarthestLabel;

    // Panels (private)
    private final PointPanel pointPanel;

    public MainView(Model model) {
        super("P3 - Parella de punts mes propera i mes llunyana");
        this.model = model;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // === Control panel (top) ===
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Controls"));

        controlPanel.add(new JLabel("N:"));
        nSpinner = new JSpinner(new SpinnerNumberModel(1000, 2, 10_000_000, 100));
        nSpinner.setPreferredSize(new Dimension(100, 25));
        controlPanel.add(nSpinner);

        controlPanel.add(new JLabel("Distribucio:"));
        distributionCombo = new JComboBox<>(PointCloud.Distribution.values());
        controlPanel.add(distributionCombo);

        generateButton = new JButton("Generar");
        controlPanel.add(generateButton);

        runButton = new JButton("Executar");
        runButton.setEnabled(false);
        controlPanel.add(runButton);

        benchmarkButton = new JButton("Benchmark");
        controlPanel.add(benchmarkButton);

        add(controlPanel, BorderLayout.NORTH);

        // === Point visualization (center) ===
        pointPanel = new PointPanel();
        add(pointPanel, BorderLayout.CENTER);

        // === Results panel (right) ===
        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBorder(BorderFactory.createTitledBorder("Resultats"));
        resultsPanel.setPreferredSize(new Dimension(320, 0));

        closestBruteLabel = new JLabel("Mes propera (Bruta): -");
        closestDCLabel    = new JLabel("Mes propera (D&C): -");
        farthestLabel     = new JLabel("Mes llunyana: -");
        timeBruteLabel    = new JLabel("Temps Bruta: -");
        timeDCLabel       = new JLabel("Temps D&C: -");
        timeFarthestLabel = new JLabel("Temps Mes lluny: -");

        for (JLabel label : new JLabel[]{closestBruteLabel, closestDCLabel, farthestLabel,
                                          timeBruteLabel, timeDCLabel, timeFarthestLabel}) {
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            resultsPanel.add(label);
        }
        resultsPanel.add(Box.createVerticalGlue());
        add(resultsPanel, BorderLayout.EAST);

        // Wire buttons — fire events through listener, no controller reference needed
        generateButton.addActionListener(e -> {
            if (listener != null) listener.onGenerate(
                (int) nSpinner.getValue(),
                (PointCloud.Distribution) distributionCombo.getSelectedItem(),
                RANGE_MIN, RANGE_MAX
            );
        });
        runButton.addActionListener(e -> {
            if (listener != null) listener.onRun();
        });
        benchmarkButton.addActionListener(e -> {
            if (listener != null) listener.onOpenBenchmark();
        });

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 700));
    }

    public void addViewListener(ViewListener listener) {
        this.listener = listener;
    }

    // === View update methods (called by controller) ===

    public void showPoints(double rangeMin, double rangeMax) {
        pointPanel.setData(model.getPoints(), rangeMin, rangeMax);
    }

    public void updateResults() {
        PointPair closestBrute = model.getClosestBrute();
        PointPair closestDC   = model.getClosestDC();
        PointPair farthest    = model.getFarthest();

        if (closestBrute != null) {
            closestBruteLabel.setText(formatPairHtml("Mes propera (Bruta)", closestBrute));
            timeBruteLabel.setText(String.format("Temps Bruta: %.4f ms", model.getTimeBruteMs()));
        }
        if (closestDC != null) {
            closestDCLabel.setText(formatPairHtml("Mes propera (D&C)", closestDC));
            timeDCLabel.setText(String.format("Temps D&C: %.4f ms", model.getTimeDCMs()));
        }
        if (farthest != null) {
            farthestLabel.setText(formatPairHtml("Mes llunyana", farthest));
            timeFarthestLabel.setText(String.format("Temps Mes lluny: %.4f ms", model.getTimeFarthestMs()));
        }

        pointPanel.setResults(closestBrute, farthest);
    }

    public void clearResults() {
        closestBruteLabel.setText("Mes propera (Bruta): -");
        closestDCLabel.setText("Mes propera (D&C): -");
        farthestLabel.setText("Mes llunyana: -");
        timeBruteLabel.setText("Temps Bruta: -");
        timeDCLabel.setText("Temps D&C: -");
        timeFarthestLabel.setText("Temps Mes lluny: -");
    }

    public void setRunning(boolean running) {
        generateButton.setEnabled(!running);
        runButton.setEnabled(!running);
        benchmarkButton.setEnabled(!running);
        setCursor(running
            ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
            : Cursor.getDefaultCursor());
    }

    public void setRunButtonEnabled(boolean enabled) {
        runButton.setEnabled(enabled);
    }

    private String formatPairHtml(String title, PointPair pair) {
        return String.format("<html><b>%s</b><br>P1: %s<br>P2: %s<br>d = %.6f</html>",
                title, pair.p1(), pair.p2(), pair.distance());
    }
}
