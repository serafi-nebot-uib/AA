package com.serafinebot.p4.view;

import com.serafinebot.p4.model.benchmark.BenchmarkConfig;
import com.serafinebot.p4.model.benchmark.BenchmarkCsvWriter;
import com.serafinebot.p4.model.benchmark.BenchmarkPoint;
import com.serafinebot.p4.model.benchmark.BenchmarkProgressSnapshot;
import com.serafinebot.p4.model.benchmark.BenchmarkProfile;
import com.serafinebot.p4.model.benchmark.BenchmarkReport;
import com.serafinebot.p4.model.benchmark.BenchmarkSizeUnit;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls and graphs for the optional performance comparison section.
 */
public final class BenchmarkPanel extends JPanel {

    private static final DecimalFormat SIZE_FIELD_FORMAT = new DecimalFormat("0.###");

    private static final Map<PriorityQueueStrategy, Color> STRATEGY_COLORS = Map.of(
        PriorityQueueStrategy.BINARY_HEAP, new Color(54, 111, 214),
        PriorityQueueStrategy.ORDERED_LIST, new Color(232, 126, 39),
        PriorityQueueStrategy.DICHOTOMIC_LIST, new Color(55, 156, 91),
        PriorityQueueStrategy.FIBONACCI_HEAP, new Color(142, 84, 201)
    );
    private static final Map<BenchmarkProfile, Color> PROFILE_COLORS = Map.of(
        BenchmarkProfile.TEXT_LIKE, new Color(54, 111, 214),
        BenchmarkProfile.LOW_ENTROPY, new Color(55, 156, 91),
        BenchmarkProfile.RANDOM_BYTES, new Color(184, 73, 109)
    );

    private final JTextField minSizeField = new JTextField("4", 6);
    private final JTextField maxSizeField = new JTextField("256", 6);
    private final JTextField pointCountField = new JTextField("6", 6);
    private final JTextField repetitionsField = new JTextField("3", 6);
    private final JComboBox<BenchmarkSizeUnit> sizeUnitCombo = new JComboBox<>(BenchmarkSizeUnit.values());
    private final JComboBox<BenchmarkProfile> timeProfileCombo = new JComboBox<>(BenchmarkProfile.values());
    private final JButton runButton = new JButton("Executa comparatives");
    private final JButton exportButton = new JButton("Exporta CSV");
    private final JLabel progressLabel = new JLabel("Comparativa inactiva.");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final BenchmarkGraphPanel compressionGraph = new BenchmarkGraphPanel();
    private final BenchmarkGraphPanel decompressionGraph = new BenchmarkGraphPanel();
    private final BenchmarkGraphPanel compressionRateGraph = new BenchmarkGraphPanel();
    private final BenchmarkBarChartPanel strategyTimeChart = new BenchmarkBarChartPanel();
    private final BenchmarkGraphPanel entropyGraph = new BenchmarkGraphPanel();

    private BenchmarkReport report;

    public BenchmarkPanel() {
        super(new BorderLayout(10, 10));
        setOpaque(false);
        add(createTopPanel(), BorderLayout.NORTH);
        add(createGraphsPanel(), BorderLayout.CENTER);
        progressBar.setStringPainted(false);

        timeProfileCombo.addActionListener(event -> refreshGraphs());
        sizeUnitCombo.addActionListener(event -> clampSizeFieldsToUnit());
        exportButton.addActionListener(event -> exportCsv());
        showEmptyState();
    }

    public void addRunListener(java.awt.event.ActionListener listener) {
        runButton.addActionListener(listener);
    }

    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        minSizeField.setEnabled(!running);
        maxSizeField.setEnabled(!running);
        pointCountField.setEnabled(!running);
        repetitionsField.setEnabled(!running);
        sizeUnitCombo.setEnabled(!running);
        timeProfileCombo.setEnabled(!running);
        exportButton.setEnabled(!running && report != null);
    }

    public void resetProgress() {
        progressBar.setValue(0);
        progressLabel.setText("Comparativa inactiva.");
    }

    public void updateProgress(BenchmarkProgressSnapshot snapshot) {
        int percent = (int) Math.round(snapshot.completion() * 100.0);
        progressBar.setValue(percent);
        progressLabel.setText(String.format(
            "%s • %s • %s • pas %d/%d • %d%%",
            snapshot.profile(),
            snapshot.strategy(),
            formatSize(snapshot.sizeBytes()),
            snapshot.completedSteps(),
            snapshot.totalSteps(),
            percent
        ));
    }

    public BenchmarkConfig readConfig() {
        try {
            double minSize = Double.parseDouble(minSizeField.getText().trim());
            double maxSize = Double.parseDouble(maxSizeField.getText().trim());
            int pointCount = Integer.parseInt(pointCountField.getText().trim());
            int repetitions = Integer.parseInt(repetitionsField.getText().trim());

            BenchmarkSizeUnit unit = (BenchmarkSizeUnit) sizeUnitCombo.getSelectedItem();
            long minBytes = Math.round(minSize * unit.multiplier());
            long maxBytes = Math.round(maxSize * unit.multiplier());
            if (minSize <= 0.0 || maxSize <= 0.0) {
                throw new IllegalArgumentException("Les mides de la comparativa han de ser positives.");
            }
            if (minBytes > Integer.MAX_VALUE || maxBytes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("La comparativa sintetica es limita a aproximadament 2 GiB per mostra. Redueix la mida o usa una unitat mes petita.");
            }
            return new BenchmarkConfig((int) minBytes, (int) maxBytes, pointCount, repetitions);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Els camps de mida de la comparativa han de contenir nombres valids.");
        }
    }

    public void showReport(BenchmarkReport report) {
        this.report = report;
        exportButton.setEnabled(true);
        progressBar.setValue(100);
        progressLabel.setText("Comparativa completada.");
        refreshGraphs();
    }

    private JPanel createTopPanel() {
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(createControlsPanel(), BorderLayout.NORTH);
        top.add(createProgressPanel(), BorderLayout.SOUTH);
        return top;
    }

    private JPanel createControlsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(189, 198, 210)), "Configuracio de la comparativa"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;

        gbc.gridx = 0;
        panel.add(new JLabel("Mida minima:"), gbc);
        gbc.gridx = 1;
        panel.add(minSizeField, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("Mida maxima:"), gbc);
        gbc.gridx = 3;
        panel.add(maxSizeField, gbc);

        gbc.gridx = 4;
        panel.add(new JLabel("Unitat:"), gbc);
        gbc.gridx = 5;
        panel.add(sizeUnitCombo, gbc);

        gbc.gridx = 6;
        panel.add(new JLabel("Punts:"), gbc);
        gbc.gridx = 7;
        panel.add(pointCountField, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        panel.add(new JLabel("Repeticions:"), gbc);
        gbc.gridx = 1;
        panel.add(repetitionsField, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("Perfil pels temps:"), gbc);
        gbc.gridx = 3;
        gbc.gridwidth = 2;
        panel.add(timeProfileCombo, gbc);

        gbc.gridx = 5;
        gbc.gridwidth = 1;
        panel.add(exportButton, gbc);

        gbc.gridx = 7;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(runButton, gbc);

        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 6));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(189, 198, 210)), "Progres"));
        panel.add(progressLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        return panel;
    }

    private JTabbedPane createGraphsPanel() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Temps de compressio", compressionGraph);
        tabs.addTab("Temps de descompressio", decompressionGraph);
        tabs.addTab("Taxa de compressio", compressionRateGraph);
        tabs.addTab("Temps per estrategia", strategyTimeChart);
        tabs.addTab("Entropia vs codi", entropyGraph);
        return tabs;
    }

    private void refreshGraphs() {
        if (report == null || report.points().isEmpty()) {
            showEmptyState();
            return;
        }

        BenchmarkProfile selectedProfile = (BenchmarkProfile) timeProfileCombo.getSelectedItem();
        compressionGraph.setGraph(
            "Temps de compressio segons la mida",
            "Mida (bytes)",
            "Temps (ms)",
            "No hi ha dades de compressio.",
            timeSeries(selectedProfile, true)
        );
        decompressionGraph.setGraph(
            "Temps de descompressio segons la mida",
            "Mida (bytes)",
            "Temps (ms)",
            "No hi ha dades de descompressio.",
            timeSeries(selectedProfile, false)
        );
        compressionRateGraph.setGraph(
            "Taxa de compressio segons la mida",
            "Mida (bytes)",
            "Compressio (%)",
            "No hi ha dades de taxa de compressio.",
            compressionRateSeries(selectedProfile)
        );
        strategyTimeChart.setChart(
            "Temps mitja de compressio per estrategia",
            "Temps mitja (ms)",
            "No hi ha dades de temps per estrategia.",
            strategyTimeBars(selectedProfile)
        );
        entropyGraph.setGraph(
            "Entropia vs longitud mitjana del codi",
            "Entropia (bits/simbol)",
            "Longitud mitjana (bits/simbol)",
            "No hi ha dades d'entropia.",
            entropySeries()
        );
    }

    private void showEmptyState() {
        compressionGraph.setGraph("Temps de compressio segons la mida", "Mida (bytes)", "Temps (ms)", "Executa una comparativa per veure el graf.", List.of());
        decompressionGraph.setGraph("Temps de descompressio segons la mida", "Mida (bytes)", "Temps (ms)", "Executa una comparativa per veure el graf.", List.of());
        compressionRateGraph.setGraph("Taxa de compressio segons la mida", "Mida (bytes)", "Compressio (%)", "Executa una comparativa per veure el graf.", List.of());
        strategyTimeChart.setChart("Temps mitja de compressio per estrategia", "Temps mitja (ms)", "Executa una comparativa per veure el graf.", List.of());
        entropyGraph.setGraph("Entropia vs longitud mitjana del codi", "Entropia", "Longitud mitjana", "Executa una comparativa per veure el graf.", List.of());
        exportButton.setEnabled(false);
    }

    private List<BenchmarkGraphPanel.Series> timeSeries(BenchmarkProfile selectedProfile, boolean compression) {
        List<BenchmarkGraphPanel.Series> series = new ArrayList<>();
        for (PriorityQueueStrategy strategy : PriorityQueueStrategy.values()) {
            List<Point2D.Double> points = new ArrayList<>();
            for (BenchmarkPoint point : report.points()) {
                if (point.profile() == selectedProfile && point.strategy() == strategy) {
                    points.add(new Point2D.Double(point.sizeBytes(), compression ? point.compressionMillis() : point.decompressionMillis()));
                }
            }
            series.add(new BenchmarkGraphPanel.Series(strategy.toString(), STRATEGY_COLORS.get(strategy), true, points));
        }
        return series;
    }

    private List<BenchmarkGraphPanel.Series> entropySeries() {
        List<BenchmarkGraphPanel.Series> series = new ArrayList<>();
        for (BenchmarkProfile profile : BenchmarkProfile.values()) {
            Map<Integer, Point2D.Double> uniquePoints = new LinkedHashMap<>();
            for (BenchmarkPoint point : report.points()) {
                if (point.profile() == profile && point.strategy() == PriorityQueueStrategy.BINARY_HEAP) {
                    uniquePoints.put(point.sizeBytes(), new Point2D.Double(point.entropy(), point.averageCodeLength()));
                }
            }
            series.add(new BenchmarkGraphPanel.Series(profile.toString(), PROFILE_COLORS.get(profile), false, List.copyOf(uniquePoints.values())));
        }
        return series;
    }

    private List<BenchmarkGraphPanel.Series> compressionRateSeries(BenchmarkProfile selectedProfile) {
        List<BenchmarkGraphPanel.Series> series = new ArrayList<>();
        for (PriorityQueueStrategy strategy : PriorityQueueStrategy.values()) {
            List<Point2D.Double> points = new ArrayList<>();
            for (BenchmarkPoint point : report.points()) {
                if (point.profile() == selectedProfile && point.strategy() == strategy) {
                    points.add(new Point2D.Double(point.sizeBytes(), point.compressionPercentage()));
                }
            }
            series.add(new BenchmarkGraphPanel.Series(strategy.toString(), STRATEGY_COLORS.get(strategy), true, points));
        }
        return series;
    }

    private List<BenchmarkBarChartPanel.Bar> strategyTimeBars(BenchmarkProfile selectedProfile) {
        List<BenchmarkBarChartPanel.Bar> bars = new ArrayList<>();
        for (PriorityQueueStrategy strategy : PriorityQueueStrategy.values()) {
            double total = 0.0;
            int count = 0;
            for (BenchmarkPoint point : report.points()) {
                if (point.profile() == selectedProfile && point.strategy() == strategy) {
                    total += point.compressionMillis();
                    count++;
                }
            }
            if (count > 0) {
                bars.add(new BenchmarkBarChartPanel.Bar(strategy.toString(), STRATEGY_COLORS.get(strategy), total / count));
            }
        }
        return bars;
    }

    private String format(double value) {
        return String.format("%.3f", value);
    }

    private String formatSize(long sizeBytes) {
        String[] units = {"B", "KiB", "MiB", "GiB"};
        double value = sizeBytes;
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        if (unitIndex == 0) {
            return sizeBytes + " " + units[unitIndex];
        }
        return String.format("%.2f %s", value, units[unitIndex]);
    }

    private void clampSizeFieldsToUnit() {
        BenchmarkSizeUnit unit = (BenchmarkSizeUnit) sizeUnitCombo.getSelectedItem();
        if (unit == null) {
            return;
        }
        double maxAllowed = Integer.MAX_VALUE / (double) unit.multiplier();
        clampField(minSizeField, maxAllowed);
        clampField(maxSizeField, maxAllowed);
    }

    private void clampField(JTextField field, double maxAllowed) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (value > maxAllowed) {
                field.setText(SIZE_FIELD_FORMAT.format(maxAllowed));
            }
        } catch (NumberFormatException ignored) {
            // Leave validation to readConfig().
        }
    }

    private void exportCsv() {
        if (report == null) {
            JOptionPane.showMessageDialog(this, "Encara no hi ha cap comparativa per exportar.", "Exportacio CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Desa la comparativa en format CSV");
        chooser.setSelectedFile(new java.io.File("comparativa.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new java.io.File(file.getAbsolutePath() + ".csv");
        }

        try {
            BenchmarkCsvWriter.write(report, file.toPath());
            JOptionPane.showMessageDialog(this, "CSV exportat correctament a " + file.getName() + '.', "Exportacio CSV", JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException exception) {
            JOptionPane.showMessageDialog(this, "No s'ha pogut exportar el CSV: " + exception.getMessage(), "Exportacio CSV", JOptionPane.ERROR_MESSAGE);
        }
    }
}
