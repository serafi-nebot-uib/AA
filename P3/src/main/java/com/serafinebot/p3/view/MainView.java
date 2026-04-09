package com.serafinebot.p3.view;

import com.serafinebot.p3.model.DistributionParams;
import com.serafinebot.p3.model.Model;
import com.serafinebot.p3.model.PointCloud;
import com.serafinebot.p3.model.PointPair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

public class MainView extends JFrame {

    public static final double RANGE_MIN = 0;
    public static final double RANGE_MAX = 1000;

    private final Model model;
    private ViewListener listener;

    private final JSpinner nSpinner;
    private final JComboBox<PointCloud.Distribution> distributionCombo;
    private final DistributionParamPanel paramPanel;
    private final JButton runButton;
    private final JButton stopButton;
    private final JButton benchmarkButton;

    private final JLabel closestBruteLabel;
    private final JLabel closestDCLabel;
    private final JLabel closestBucketLabel;
    private final JLabel farthestLabel;
    private final JLabel timeBruteLabel;
    private final JLabel timeDCLabel;
    private final JLabel timeBucketLabel;
    private final JLabel timeFarthestLabel;

    private final PointPanel pointPanel;

    // Debounce timer: fires 300 ms after the last parameter change
    private final Timer debounceTimer;

    public MainView(Model model) {
        super("P3 - Parella de punts mes propera i mes llunyana");
        this.model = model;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // === Control panel (single row, buttons pinned right) ===
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Controls"));

        // Left: N + distribution + params
        JPanel leftSide = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));

        leftSide.add(new JLabel("N:"));
        nSpinner = new JSpinner(new SpinnerNumberModel(1000, 2, 10_000_000, 100));
        nSpinner.setPreferredSize(new Dimension(100, 25));
        leftSide.add(nSpinner);

        distributionCombo = new JComboBox<>(PointCloud.Distribution.values());
        leftSide.add(distributionCombo);

        paramPanel = new DistributionParamPanel();
        paramPanel.setDistribution((PointCloud.Distribution) distributionCombo.getSelectedItem());
        leftSide.add(paramPanel);

        controlPanel.add(leftSide, BorderLayout.CENTER);

        // Right: action buttons
        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        runButton = new JButton("Executar");
        runButton.setEnabled(false);
        stopButton = new JButton("Aturar");
        stopButton.setEnabled(false);
        benchmarkButton = new JButton("Benchmark");
        rightSide.add(runButton);
        rightSide.add(stopButton);
        rightSide.add(benchmarkButton);

        controlPanel.add(rightSide, BorderLayout.EAST);
        add(controlPanel, BorderLayout.NORTH);

        // === Point visualization (center) ===
        pointPanel = new PointPanel();
        add(pointPanel, BorderLayout.CENTER);

        // === Results panel (right) ===
        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBorder(BorderFactory.createTitledBorder("Resultats"));
        resultsPanel.setPreferredSize(new Dimension(320, 0));

        closestBruteLabel  = new JLabel("Mes propera (Bruta): -");
        closestDCLabel     = new JLabel("Mes propera (D&C): -");
        closestBucketLabel = new JLabel("Mes propera (D&C Bucket): -");
        farthestLabel      = new JLabel("Mes llunyana: -");
        timeBruteLabel     = new JLabel("Temps Bruta: -");
        timeDCLabel        = new JLabel("Temps D&C: -");
        timeBucketLabel    = new JLabel("Temps D&C Bucket: -");
        timeFarthestLabel  = new JLabel("Temps Mes lluny: -");

        for (JLabel label : new JLabel[]{
                closestBruteLabel, closestDCLabel, closestBucketLabel, farthestLabel,
                timeBruteLabel, timeDCLabel, timeBucketLabel, timeFarthestLabel}) {
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            resultsPanel.add(label);
        }
        resultsPanel.add(Box.createVerticalGlue());
        add(resultsPanel, BorderLayout.EAST);

        // === Debounce timer ===
        debounceTimer = new Timer(300, e -> triggerGenerate());
        debounceTimer.setRepeats(false);

        // === Wire parameter change listeners ===
        nSpinner.addChangeListener(e -> scheduleGenerate());

        distributionCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                paramPanel.setDistribution((PointCloud.Distribution) e.getItem());
                scheduleGenerate();
            }
        });

        paramPanel.setOnChange(this::scheduleGenerate);

        // === Wire action buttons ===
        runButton.addActionListener(e -> {
            if (listener != null) listener.onRun();
        });
        stopButton.addActionListener(e -> {
            if (listener != null) listener.onStop();
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

    private void scheduleGenerate() {
        debounceTimer.restart();
    }

    private void triggerGenerate() {
        if (listener != null) listener.onGenerate(
            (int) nSpinner.getValue(),
            (PointCloud.Distribution) distributionCombo.getSelectedItem(),
            RANGE_MIN, RANGE_MAX,
            paramPanel.read()
        );
    }

    // === View update methods (called by controller) ===

    public void showPoints(double rangeMin, double rangeMax) {
        pointPanel.setData(model.getPoints(), rangeMin, rangeMax);
    }

    public void updateResults() {
        PointPair closestBrute  = model.getClosestBrute();
        PointPair closestDC     = model.getClosestDC();
        PointPair closestBucket = model.getClosestBucket();
        PointPair farthest      = model.getFarthest();

        if (closestBrute != null) {
            closestBruteLabel.setText(formatPairHtml("Mes propera (Bruta)", closestBrute));
            timeBruteLabel.setText(String.format("Temps Bruta: %.4f ms", model.getTimeBruteMs()));
        }
        if (closestDC != null) {
            closestDCLabel.setText(formatPairHtml("Mes propera (D&C)", closestDC));
            timeDCLabel.setText(String.format("Temps D&C: %.4f ms", model.getTimeDCMs()));
        }
        if (closestBucket != null) {
            closestBucketLabel.setText(formatPairHtml("Mes propera (D&C Bucket)", closestBucket));
            timeBucketLabel.setText(String.format("Temps D&C Bucket: %.4f ms", model.getTimeBucketMs()));
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
        closestBucketLabel.setText("Mes propera (D&C Bucket): -");
        farthestLabel.setText("Mes llunyana: -");
        timeBruteLabel.setText("Temps Bruta: -");
        timeDCLabel.setText("Temps D&C: -");
        timeBucketLabel.setText("Temps D&C Bucket: -");
        timeFarthestLabel.setText("Temps Mes lluny: -");
    }

    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        stopButton.setEnabled(running);
        benchmarkButton.setEnabled(!running);
        nSpinner.setEnabled(!running);
        distributionCombo.setEnabled(!running);
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
