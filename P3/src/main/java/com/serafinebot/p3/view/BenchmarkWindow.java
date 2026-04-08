package com.serafinebot.p3.view;

import com.serafinebot.p3.controller.Controller;
import com.serafinebot.p3.model.Benchmark;
import com.serafinebot.p3.model.PointCloud;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkWindow extends JDialog {

    private final Controller controller;

    private final JTextField nValuesField;
    private final JComboBox<PointCloud.Distribution> distributionCombo;
    private final JButton runButton;
    private final JCheckBox logScaleCheck;
    private final JCheckBox[] seriesChecks;

    private final BenchmarkGraphPanel graphPanel;
    private final DefaultTableModel tableModel;

    private static final String DEFAULT_N_VALUES = "100,500,1000,2000,5000,10000,20000,50000";

    public BenchmarkWindow(Frame owner, Controller controller) {
        super(owner, "Benchmark — Comparació d'Algorismes", false);
        this.controller = controller;

        setLayout(new BorderLayout(5, 5));

        // === Controls (top) ===
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Configuració"));

        controlPanel.add(new JLabel("Valors de N (separats per comes):"));
        nValuesField = new JTextField(DEFAULT_N_VALUES, 35);
        controlPanel.add(nValuesField);

        controlPanel.add(new JLabel("Distribució:"));
        distributionCombo = new JComboBox<>(PointCloud.Distribution.values());
        controlPanel.add(distributionCombo);

        runButton = new JButton("Executar Benchmark");
        controlPanel.add(runButton);

        add(controlPanel, BorderLayout.NORTH);

        // === Chart (center) ===
        graphPanel = new BenchmarkGraphPanel();
        graphPanel.setPreferredSize(new Dimension(800, 400));
        add(graphPanel, BorderLayout.CENTER);

        // === Options + table (right) ===
        JPanel rightPanel = new JPanel(new BorderLayout(0, 5));
        rightPanel.setPreferredSize(new Dimension(200, 0));

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
        add(rightPanel, BorderLayout.EAST);

        // Numeric table — full width at bottom
        String[] cols = {"N", "Bruta (ms)", "D&C (ms)", "Mes lluny (ms)", "Speedup (Bruta/D&C)"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Dades"));
        tableScroll.setPreferredSize(new Dimension(0, 180));
        add(tableScroll, BorderLayout.SOUTH);

        // === Wire button ===
        runButton.addActionListener(e -> runBenchmark());

        pack();
        setLocationRelativeTo(owner);
        setMinimumSize(new Dimension(1000, 550));
    }

    private void runBenchmark() {
        int[] nValues = parseNValues();
        if (nValues == null) return;

        PointCloud.Distribution dist = (PointCloud.Distribution) distributionCombo.getSelectedItem();

        runButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Benchmark.BenchmarkEntry[], Void> worker = new SwingWorker<>() {
            @Override
            protected Benchmark.BenchmarkEntry[] doInBackground() {
                controller.runBenchmarkSeries(nValues, dist, MainView.RANGE_MIN, MainView.RANGE_MAX);
                return controller.getBenchmarkResults();
            }

            @Override
            protected void done() {
                try {
                    Benchmark.BenchmarkEntry[] results = get();
                    graphPanel.setData(results);
                    updateTable(results);
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
            double brute   = e.bruteForce().averageTimeMs();
            double dc      = e.divideConquer().averageTimeMs();
            double farthest = e.farthestPair().averageTimeMs();
            double speedup = dc > 0 ? brute / dc : 0;
            tableModel.addRow(new Object[]{
                e.n(),
                String.format("%.4f", brute),
                String.format("%.4f", dc),
                String.format("%.4f", farthest),
                String.format("%.2fx", speedup)
            });
        }
    }
}
