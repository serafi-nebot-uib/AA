package com.serafinebot.p4.view;

import com.serafinebot.p4.model.benchmark.BenchmarkConfig;
import com.serafinebot.p4.model.benchmark.BenchmarkCsvWriter;
import com.serafinebot.p4.model.benchmark.BenchmarkModePoint;
import com.serafinebot.p4.model.benchmark.BenchmarkPoint;
import com.serafinebot.p4.model.benchmark.BenchmarkProgressSnapshot;
import com.serafinebot.p4.model.benchmark.BenchmarkReport;
import com.serafinebot.p4.model.benchmark.BenchmarkVariant;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controls and graphs for the performance comparison section.
 */
public final class BenchmarkPanel extends JPanel {

    private static final String DEFAULT_CORPUS_DIRECTORY = detectDefaultCorpusDirectory();

    private static final Map<PriorityQueueStrategy, Color> STRATEGY_COLORS = Map.of(
        PriorityQueueStrategy.BINARY_HEAP, new Color(54, 111, 214),
        PriorityQueueStrategy.DICHOTOMIC_LIST, new Color(232, 126, 39),
        PriorityQueueStrategy.FIBONACCI_HEAP, new Color(142, 84, 201)
    );
    private static final Map<BenchmarkVariant, Color> VARIANT_COLORS = Map.of(
        BenchmarkVariant.HUFFMAN_1_BYTE, new Color(54, 111, 214),
        BenchmarkVariant.HUFFMAN_2_BYTE, new Color(232, 126, 39),
        BenchmarkVariant.HUFFMAN_BLOCK_1_BYTE, new Color(55, 156, 91),
        BenchmarkVariant.HUFFMAN_BLOCK_2_BYTE, new Color(142, 84, 201)
    );

    private final JTextField corpusDirectoryField = new JTextField(DEFAULT_CORPUS_DIRECTORY, 28);
    private final JButton corpusBrowseButton = new JButton("Examina...");
    private final JTextField repetitionsField = new JTextField("3", 6);
    private final JButton runButton = new JButton("Executa comparatives");
    private final JButton exportButton = new JButton("Exporta CSV");
    private final JLabel progressLabel = new JLabel("Comparativa inactiva.");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final BenchmarkGraphPanel compressionTreeBuildGraph = new BenchmarkGraphPanel();
    private final BenchmarkGraphPanel decompressionTreeBuildGraph = new BenchmarkGraphPanel();
    private final BenchmarkGraphPanel modeCompressionGraph = new BenchmarkGraphPanel();
    private final BenchmarkGraphPanel modeDecompressionGraph = new BenchmarkGraphPanel();
    private final BenchmarkGraphPanel compressionRateGraph = new BenchmarkGraphPanel();
    private final BenchmarkGraphPanel entropyGraph = new BenchmarkGraphPanel();

    private BenchmarkReport report;

    public BenchmarkPanel() {
        super(new BorderLayout(10, 10));
        setOpaque(false);
        add(createTopPanel(), BorderLayout.NORTH);
        add(createGraphsPanel(), BorderLayout.CENTER);
        progressBar.setStringPainted(false);
        exportButton.addActionListener(event -> exportCsv());
        corpusBrowseButton.addActionListener(event -> browseForCorpus());
        showEmptyState();
    }

    private void browseForCorpus() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona el directori de fitxers");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = corpusDirectoryField.getText().trim();
        if (!current.isEmpty()) {
            try {
                Path currentPath = resolveCorpusDirectory(current);
                if (Files.isDirectory(currentPath)) {
                    chooser.setCurrentDirectory(currentPath.toFile());
                }
            } catch (InvalidPathException ignored) {
                // Fall back to default directory.
            }
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File selected = chooser.getSelectedFile();
        if (selected != null) {
            corpusDirectoryField.setText(selected.getAbsolutePath());
        }
    }

    public void addRunListener(java.awt.event.ActionListener listener) {
        runButton.addActionListener(listener);
    }

    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        corpusDirectoryField.setEnabled(!running);
        corpusBrowseButton.setEnabled(!running);
        repetitionsField.setEnabled(!running);
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
            snapshot.sourceName(),
            snapshot.strategy(),
            formatSize(snapshot.sizeBytes()),
            snapshot.completedSteps(),
            snapshot.totalSteps(),
            percent
        ));
    }

    public BenchmarkConfig readConfig() {
        String corpusText = corpusDirectoryField.getText().trim();
        if (corpusText.isEmpty()) {
            throw new IllegalArgumentException("Indica un directori amb els fitxers a provar.");
        }

        try {
            Path corpusDirectory = resolveCorpusDirectory(corpusText);
            if (!Files.isDirectory(corpusDirectory)) {
                throw new IllegalArgumentException("El directori del corpus no existeix o no es valid.");
            }

            int repetitions = Integer.parseInt(repetitionsField.getText().trim());
            return new BenchmarkConfig(corpusDirectory, repetitions);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("La ruta del corpus no es valida: " + exception.getInput());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El camp de repeticions ha de contenir un nombre valid.");
        }
    }

    private static String detectDefaultCorpusDirectory() {
        for (String candidate : List.of("res", "P4/res")) {
            if (Files.isDirectory(Path.of(candidate))) {
                return candidate;
            }
        }
        return "res";
    }

    private static Path resolveCorpusDirectory(String corpusText) {
        Path direct = Path.of(corpusText);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        if (!direct.isAbsolute()) {
            Path projectRelative = Path.of("P4").resolve(direct).normalize();
            if (Files.isDirectory(projectRelative)) {
                return projectRelative;
            }
        }
        return direct;
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
        gbc.weightx = 0.0;
        panel.add(new JLabel("Directori de fitxers:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 3;
        panel.add(corpusDirectoryField, gbc);

        gbc.gridx = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        panel.add(corpusBrowseButton, gbc);

        gbc.gridx = 5;
        panel.add(exportButton, gbc);

        gbc.gridx = 6;
        panel.add(runButton, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        panel.add(new JLabel("Repeticions:"), gbc);

        gbc.gridx = 1;
        panel.add(repetitionsField, gbc);

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
        tabs.addTab("Construccio arbre (compressio)", compressionTreeBuildGraph);
        tabs.addTab("Construccio arbre (descompressio)", decompressionTreeBuildGraph);
        tabs.addTab("Temps de compressio per mode", modeCompressionGraph);
        tabs.addTab("Temps de descompressio per mode", modeDecompressionGraph);
        tabs.addTab("Taxa de compressio", compressionRateGraph);
        tabs.addTab("Entropia vs codi", entropyGraph);
        return tabs;
    }

    private void refreshGraphs() {
        if (report == null || report.points().isEmpty()) {
            showEmptyState();
            return;
        }

        compressionTreeBuildGraph.setGraph(
            "Temps de construccio de l'arbre (compressio) segons la mida",
            "Mida (bytes)",
            "Temps (ms)",
            "No hi ha dades de construccio de l'arbre.",
            treeBuildSeries(true)
        );
        decompressionTreeBuildGraph.setGraph(
            "Temps de construccio de l'arbre (descompressio) segons la mida",
            "Mida (bytes)",
            "Temps (ms)",
            "No hi ha dades de construccio de l'arbre.",
            treeBuildSeries(false)
        );
        modeCompressionGraph.setGraph(
            "Temps de compressio segons la mida (per mode)",
            "Mida (bytes)",
            "Temps (ms)",
            "No hi ha dades de compressio per mode.",
            modeTimeSeries(true)
        );
        modeDecompressionGraph.setGraph(
            "Temps de descompressio segons la mida (per mode)",
            "Mida (bytes)",
            "Temps (ms)",
            "No hi ha dades de descompressio per mode.",
            modeTimeSeries(false)
        );
        compressionRateGraph.setGraph(
            "Rati de compressio segons la mida (per mode)",
            "Mida (bytes)",
            "Rati (N:1)",
            "No hi ha dades de rati de compressio.",
            compressionRateSeries()
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
        compressionTreeBuildGraph.setGraph("Temps de construccio de l'arbre (compressio)", "Mida (bytes)", "Temps (ms)", "Executa una comparativa per veure el graf.", List.of());
        decompressionTreeBuildGraph.setGraph("Temps de construccio de l'arbre (descompressio)", "Mida (bytes)", "Temps (ms)", "Executa una comparativa per veure el graf.", List.of());
        modeCompressionGraph.setGraph("Temps de compressio per mode", "Mida (bytes)", "Temps (ms)", "Executa una comparativa per veure el graf.", List.of());
        modeDecompressionGraph.setGraph("Temps de descompressio per mode", "Mida (bytes)", "Temps (ms)", "Executa una comparativa per veure el graf.", List.of());
        compressionRateGraph.setGraph("Rati de compressio segons la mida (per mode)", "Mida (bytes)", "Rati (N:1)", "Executa una comparativa per veure el graf.", List.of());
        entropyGraph.setGraph("Entropia vs longitud mitjana del codi", "Entropia", "Longitud mitjana", "Executa una comparativa per veure el graf.", List.of());
        exportButton.setEnabled(false);
    }

    private List<BenchmarkGraphPanel.Series> treeBuildSeries(boolean compression) {
        List<BenchmarkGraphPanel.Series> series = new ArrayList<>();
        for (PriorityQueueStrategy strategy : PriorityQueueStrategy.values()) {
            List<BenchmarkGraphPanel.GraphPoint> points = new ArrayList<>();
            for (BenchmarkPoint point : report.points()) {
                if (point.strategy() == strategy) {
                    points.add(new BenchmarkGraphPanel.GraphPoint(
                        point.sizeBytes(),
                        compression ? point.compressionMillis() : point.decompressionMillis(),
                        strategy == PriorityQueueStrategy.BINARY_HEAP ? point.sourceName() : null
                    ));
                }
            }
            series.add(new BenchmarkGraphPanel.Series(strategy.toString(), STRATEGY_COLORS.get(strategy), true, points));
        }
        return series;
    }

    private List<BenchmarkGraphPanel.Series> modeTimeSeries(boolean compression) {
        List<BenchmarkGraphPanel.Series> series = new ArrayList<>();
        for (BenchmarkVariant variant : BenchmarkVariant.values()) {
            List<BenchmarkGraphPanel.GraphPoint> points = new ArrayList<>();
            for (BenchmarkModePoint point : report.modePoints()) {
                if (point.variant() == variant) {
                    points.add(new BenchmarkGraphPanel.GraphPoint(
                        point.sizeBytes(),
                        compression ? point.compressionMillis() : point.decompressionMillis(),
                        variant == BenchmarkVariant.HUFFMAN_1_BYTE ? point.sourceName() : null
                    ));
                }
            }
            series.add(new BenchmarkGraphPanel.Series(variant.toString(), VARIANT_COLORS.get(variant), true, points));
        }
        return series;
    }

    private List<BenchmarkGraphPanel.Series> entropySeries() {
        List<BenchmarkGraphPanel.GraphPoint> points = new ArrayList<>();
        for (BenchmarkPoint point : report.points()) {
            if (point.strategy() == PriorityQueueStrategy.BINARY_HEAP) {
                points.add(new BenchmarkGraphPanel.GraphPoint(point.entropy(), point.averageCodeLength(), point.sourceName()));
            }
        }
        return List.of(new BenchmarkGraphPanel.Series("Fitxers", new Color(54, 111, 214), false, points));
    }

    private List<BenchmarkGraphPanel.Series> compressionRateSeries() {
        List<BenchmarkGraphPanel.Series> series = new ArrayList<>();
        for (BenchmarkVariant variant : BenchmarkVariant.values()) {
            List<BenchmarkGraphPanel.GraphPoint> points = new ArrayList<>();
            for (BenchmarkModePoint point : report.modePoints()) {
                if (point.variant() == variant) {
                    points.add(new BenchmarkGraphPanel.GraphPoint(
                        point.sizeBytes(),
                        compressionRatio(point.compressionPercentage()),
                        variant == BenchmarkVariant.HUFFMAN_1_BYTE ? point.sourceName() : null
                    ));
                }
            }
            series.add(new BenchmarkGraphPanel.Series(variant.toString(), VARIANT_COLORS.get(variant), true, points));
        }
        return series;
    }

    private double compressionRatio(double compressionPercentage) {
        return 100.0 / (100.0 - compressionPercentage);
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
