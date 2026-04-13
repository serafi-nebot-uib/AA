package com.serafinebot.p3.view;

import com.serafinebot.p3.model.Benchmark;
import com.serafinebot.p3.model.DistributionParams;
import com.serafinebot.p3.model.PointCloud;
import com.serafinebot.p3.model.Predictor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-modal dialog for running the benchmark series and inspecting results.
 * Contains the configuration controls, the cost graph, the data table, and
 * the prediction panel. Benchmark execution is offloaded to a SwingWorker.
 */
public class BenchmarkWindow extends JDialog {

    private final ViewListener listener;

    private final JTextField nValuesField;
    private final JButton runButton;
    private final JComboBox<PointCloud.Distribution> distributionCombo;
    private final DistributionParamPanel paramPanel;
    private final JCheckBox logScaleCheck;
    private final JCheckBox[] seriesChecks;

    private final BenchmarkGraphPanel graphPanel;
    private final DefaultTableModel tableModel;

    // Prediction UI
    private final JTextField predictNField;
    private final JButton predictButton;
    private final JLabel predBruteLabel;
    private final JLabel predDCLabel;
    private final JLabel predFarthestLabel;
    private final JLabel predDCBucketLabel;
    private Predictor.Constants fittedConstants;

    private static final String DEFAULT_N_VALUES = "100,500,1000,2000,5000,10000,20000,50000";

    public BenchmarkWindow(Frame owner, ViewListener listener) {
        super(owner, "Benchmark — Comparació d'Algorismes", false);
        this.listener = listener;

        setLayout(new BorderLayout(5, 5));

        // === Controls (top) — two rows inside a vertical box ===
        JPanel configWrapper = new JPanel();
        configWrapper.setLayout(new BoxLayout(configWrapper, BoxLayout.Y_AXIS));
        configWrapper.setBorder(BorderFactory.createTitledBorder("Configuració"));

        // Row 1: N values + run button
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row1.add(new JLabel("Valors de N (separats per comes):"));
        nValuesField = new JTextField(DEFAULT_N_VALUES, 35);
        row1.add(nValuesField);
        runButton = new JButton("Executar Benchmark");
        row1.add(runButton);
        configWrapper.add(row1);

        // Row 2: distribution + params (variable width — isolated from row 1)
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row2.add(new JLabel("Distribució:"));
        distributionCombo = new JComboBox<>(PointCloud.Distribution.values());
        row2.add(distributionCombo);
        paramPanel = new DistributionParamPanel();
        paramPanel.setDistribution((PointCloud.Distribution) distributionCombo.getSelectedItem());
        row2.add(paramPanel);
        configWrapper.add(row2);

        distributionCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED)
                paramPanel.setDistribution((PointCloud.Distribution) e.getItem());
        });

        add(configWrapper, BorderLayout.NORTH);

        // === Chart (center) ===
        graphPanel = new BenchmarkGraphPanel();
        graphPanel.setPreferredSize(new Dimension(800, 400));
        add(graphPanel, BorderLayout.CENTER);

        // === Right panel: options + prediction ===
        JPanel rightPanel = new JPanel(new BorderLayout(0, 5));
        rightPanel.setPreferredSize(new Dimension(220, 0));

        // -- Series visibility + scale --
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Opcions"));

        logScaleCheck = new JCheckBox("Escala logarítmica", false);
        logScaleCheck.addActionListener(e -> graphPanel.setLogScale(logScaleCheck.isSelected()));
        optionsPanel.add(logScaleCheck);
        optionsPanel.add(Box.createVerticalStrut(8));

        seriesChecks = new JCheckBox[BenchmarkGraphPanel.SERIES.length];
        for (int i = 0; i < BenchmarkGraphPanel.SERIES.length; i++) {
            BenchmarkGraphPanel.Series s = BenchmarkGraphPanel.SERIES[i];
            JCheckBox cb = new JCheckBox(s.name(), true);
            cb.setForeground(s.color());
            final int idx = i;
            cb.addActionListener(e -> graphPanel.setSeriesVisible(idx, cb.isSelected()));
            seriesChecks[i] = cb;
            optionsPanel.add(cb);
        }

        rightPanel.add(optionsPanel, BorderLayout.NORTH);

        // -- Prediction panel --
        JPanel predPanel = new JPanel();
        predPanel.setLayout(new BoxLayout(predPanel, BoxLayout.Y_AXIS));
        predPanel.setBorder(BorderFactory.createTitledBorder("Predicció"));

        JPanel predInputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        predInputRow.add(new JLabel("N:"));
        predictNField = new JTextField("100000", 8);
        predInputRow.add(predictNField);
        predictButton = new JButton("Predir");
        predictButton.setEnabled(false);
        predInputRow.add(predictButton);
        predInputRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        predPanel.add(predInputRow);

        predBruteLabel    = makePredLabel("Bruta:      -");
        predDCLabel       = makePredLabel("D&C:        -");
        predFarthestLabel = makePredLabel("Lluny:      -");
        predDCBucketLabel = makePredLabel("D&C Bucket: -");

        predPanel.add(predBruteLabel);
        predPanel.add(predDCLabel);
        predPanel.add(predFarthestLabel);
        predPanel.add(predDCBucketLabel);

        rightPanel.add(predPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // === Data table (bottom) ===
        String[] cols = {"N", "Bruta (ms)", "D&C (ms)", "Mes lluny (ms)", "D&C Bucket (ms)"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Dades"));
        tableScroll.setPreferredSize(new Dimension(0, 180));
        add(tableScroll, BorderLayout.SOUTH);

        // === Wire buttons ===
        runButton.addActionListener(e -> runBenchmark());
        predictButton.addActionListener(e -> predict());

        pack();
        setLocationRelativeTo(owner);
        setMinimumSize(new Dimension(1100, 650));
    }

    private void runBenchmark() {
        int[] nValues = parseNValues();
        if (nValues == null) return;

        PointCloud.Distribution dist = (PointCloud.Distribution) distributionCombo.getSelectedItem();
        DistributionParams params = paramPanel.read();

        runButton.setEnabled(false);
        predictButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Benchmark.BenchmarkEntry[], Void> worker = new SwingWorker<>() {
            @Override
            protected Benchmark.BenchmarkEntry[] doInBackground() {
                return listener.onRunBenchmark(nValues, dist, MainView.RANGE_MIN, MainView.RANGE_MAX, params);
            }

            @Override
            protected void done() {
                try {
                    Benchmark.BenchmarkEntry[] results = get();
                    fittedConstants = Predictor.fit(results);
                    graphPanel.setData(results);
                    graphPanel.setFittedConstants(fittedConstants);
                    updateTable(results);
                    predictButton.setEnabled(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BenchmarkWindow.this,
                            "Error durant el benchmark: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    runButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        worker.execute();
    }

    private void predict() {
        if (fittedConstants == null) return;
        long n;
        try {
            n = Long.parseLong(predictNField.getText().trim());
            if (n < 2) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Introduïu un enter >= 2.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        predBruteLabel.setText(String.format(
            "<html><b>Bruta:</b> %s</html>",
            formatTime(Predictor.predictBrute(fittedConstants.bruteForce(), n))));
        predDCLabel.setText(String.format(
            "<html><b>D&C:</b> %s</html>",
            formatTime(Predictor.predictDC(fittedConstants.divideConquer(), n))));
        predFarthestLabel.setText(String.format(
            "<html><b>Lluny:</b> %s</html>",
            formatTime(Predictor.predictFarthest(fittedConstants.farthest(), n))));
        predDCBucketLabel.setText(String.format(
            "<html><b>D&C Bucket:</b> %s</html>",
            formatTime(Predictor.predictBucket(fittedConstants.divideConquerBucket(), n))));
    }

    private int[] parseNValues() {
        String text = nValuesField.getText().trim();
        try {
            String[] parts = text.split("[,\\s]+");
            List<Integer> values = new ArrayList<>();
            for (String part : parts) {
                int n = Integer.parseInt(part.trim());
                if (n < 2) throw new NumberFormatException("N ha de ser >= 2");
                values.add(n);
            }
            if (values.isEmpty()) throw new NumberFormatException("Cap valor");
            return values.stream().mapToInt(Integer::intValue).toArray();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Valors de N invàlids. Introduïu enters >= 2 separats per comes.\nEx: 100,500,1000,5000",
                    "Error d'entrada", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void updateTable(Benchmark.BenchmarkEntry[] entries) {
        tableModel.setRowCount(0);
        for (Benchmark.BenchmarkEntry e : entries) {
            tableModel.addRow(new Object[]{
                e.n(),
                String.format("%.4f", e.bruteForce().averageTimeMs()),
                String.format("%.4f", e.divideConquer().averageTimeMs()),
                String.format("%.4f", e.farthestPair().averageTimeMs()),
                String.format("%.4f", e.divideConquerBucket().averageTimeMs())
            });
        }
    }

    private String formatTime(double ms) {
        if (ms >= 3_600_000) return String.format("%.2f h",   ms / 3_600_000);
        if (ms >= 60_000)    return String.format("%.2f min", ms / 60_000);
        if (ms >= 1_000)     return String.format("%.2f s",   ms / 1_000);
        if (ms >= 1)         return String.format("%.4f ms",  ms);
        if (ms >= 0.001)     return String.format("%.2f µs",  ms * 1_000);
        return String.format("%.2f ns", ms * 1_000_000);
    }

    private JLabel makePredLabel(String text) {
        JLabel l = new JLabel(text);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        return l;
    }
}
