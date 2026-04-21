package com.serafinebot.p4.view;

import com.serafinebot.p4.model.benchmark.BenchmarkConfig;
import com.serafinebot.p4.model.benchmark.BenchmarkProgressSnapshot;
import com.serafinebot.p4.model.benchmark.BenchmarkReport;
import com.serafinebot.p4.model.progress.ProgressSnapshot;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.CompressionReport;
import com.serafinebot.p4.model.report.CompressionResult;
import com.serafinebot.p4.model.report.DecompressionReport;
import com.serafinebot.p4.model.report.DecompressionResult;
import com.serafinebot.p4.model.report.HuffmanSymbolInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Main Swing window for the compressor application.
 */
public class MainWindow extends JFrame {

    private static final Color FRAME_BACKGROUND = new Color(238, 241, 245);
    private static final Color PANEL_BACKGROUND = new Color(248, 250, 252);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.000000");
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.00");

    private final JTextField inputField = new JTextField(42);
    private final JTextField outputField = new JTextField(42);
    private final JButton browseInputButton = new JButton("Explora");
    private final JButton browseOutputButton = new JButton("Explora");
    private final JButton processButton = new JButton("Comprimeix a .hff");
    private final JComboBox<PriorityQueueStrategy> queueCombo = new JComboBox<>();
    private final JTextArea statsArea = new JTextArea();
    private final JTextArea filesHintArea = new JTextArea();
    private final DefaultTableModel symbolTableModel = new DefaultTableModel(new Object[] {"Simbol", "Hex", "Freq", "Probabilitat", "Codi"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable symbolTable = new JTable(symbolTableModel);
    private final HuffmanTreePanel treePanel = new HuffmanTreePanel();
    private final BenchmarkPanel benchmarkPanel = new BenchmarkPanel();
    private final FileBrowserPanel fileBrowserPanel = new FileBrowserPanel();
    private final JLabel progressTextLabel = new JLabel("Inactiu • 0% (0 B / 0 B)");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel phaseLabel = new JLabel("Fase: Inactiu");
    private final JLabel etaLabel = new JLabel("Temps restant --");
    private final JLabel statusLabel = new JLabel("Preparat.", SwingConstants.RIGHT);
    private final JTabbedPane contentTabs = new JTabbedPane();

    private ViewListener viewListener;

    public MainWindow() {
        super("P4 Compressor Huffman");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        getContentPane().setBackground(FRAME_BACKGROUND);
        setLayout(new BorderLayout());

        queueCombo.setModel(new DefaultComboBoxModel<>(PriorityQueueStrategy.values()));
        configureTextAreas();
        configureTables();
        installInputAutoSync();
        fileBrowserPanel.setFileActivationListener(this::applyBrowserPathToInput);

        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        clearResults();
        installActions();

        pack();
        setLocationRelativeTo(null);
    }

    public void setViewListener(ViewListener viewListener) {
        this.viewListener = viewListener;
    }

    public void showWindow() {
        setVisible(true);
    }

    @Override
    public void dispose() {
        fileBrowserPanel.disposeBrowser();
        super.dispose();
    }

    public void setRunning(boolean running) {
        browseInputButton.setEnabled(!running);
        browseOutputButton.setEnabled(!running);
        processButton.setEnabled(!running);
        queueCombo.setEnabled(!running);
        inputField.setEnabled(!running);
        outputField.setEnabled(!running);
        fileBrowserPanel.setBrowserEnabled(!running);
        benchmarkPanel.setRunning(running);
    }

    public void updateProgress(ProgressSnapshot snapshot) {
        int percent = (int) Math.round(snapshot.completion() * 100.0);
        progressBar.setValue(percent);
        progressTextLabel.setText(String.format("%s • %d%% (%s / %s)",
            formatPhase(snapshot),
            percent,
            formatBytes(snapshot.processedBytes()),
            formatBytes(snapshot.totalBytes())));
        phaseLabel.setText("Fase: " + formatPhase(snapshot));
        etaLabel.setText(snapshot.estimatedRemainingMillis() < 0L ? "Temps restant --" : "Temps restant " + formatDuration(snapshot.estimatedRemainingMillis()));
    }

    public void showCompressionReport(CompressionReport report) {
        CompressionResult result = report.result();
        statsArea.setText(String.join("\n",
            "Operacio: Compressio",
            "Estrategia: " + result.mode(),
            "Cua: " + result.priorityQueueStrategy(),
            "Mida original: " + result.originalSize() + " bytes",
            "Mida de l'arxiu: " + result.archiveSize() + " bytes",
            "Mida de la capcalera: " + result.overheadSize() + " bytes",
            "Mida de la carrega: " + result.payloadSize() + " bytes",
            "Simbols diferents: " + result.distinctSymbolCount(),
            "Entropia: " + formatDecimal(result.entropy()) + " bits/simbol",
            "Longitud mitjana Huffman: " + formatDecimal(result.averageHuffmanCodeLength()) + " bits/simbol",
            "Compressio: " + formatDecimal(result.compressionPercentage()) + "%",
            "Temps: " + result.elapsedMillis() + " ms"
        ));
        populateSymbols(report.symbols());
        treePanel.setTree(report.tree(), "No hi ha cap arbre de Huffman disponible.");
        contentTabs.setSelectedIndex(0);
    }

    public void showDecompressionReport(DecompressionReport report) {
        DecompressionResult result = report.result();
        statsArea.setText(String.join("\n",
            "Operacio: Descompressio",
            "Estrategia: " + result.mode(),
            "Mida de l'arxiu: " + result.archiveSize() + " bytes",
            "Mida restaurada: " + result.restoredSize() + " bytes",
            "Temps: " + result.elapsedMillis() + " ms"
        ));
        populateSymbols(report.symbols());
        treePanel.setTree(report.tree(), "Arxiu en mode emmagatzemat: no hi ha cap arbre de Huffman disponible.");
        contentTabs.setSelectedIndex(0);
    }

    public void clearResults() {
        statsArea.setText("Encara no s'ha executat cap operacio. Fes doble clic damunt un fitxer de l'explorador per carregar-lo a la ruta d'entrada. La ruta de sortida es deriva automaticament de l'entrada, pero la pots editar manualment despres.");
        symbolTableModel.setRowCount(0);
        treePanel.setTree(null, "No hi ha cap arbre de Huffman disponible.");
        progressBar.setValue(0);
        progressTextLabel.setText("Inactiu • 0% (0 B / 0 B)");
        phaseLabel.setText("Fase: Inactiu");
        etaLabel.setText("Temps restant --");
        filesHintArea.setText("Usa l'explorador incrustat com a navegador principal. Fes doble clic damunt un fitxer normal per preparar la compressio a .hff, o damunt un arxiu .hff existent per canviar automaticament al mode d'extraccio.");
        updateProcessButtonLabel();
    }

    public void showBenchmarkReport(BenchmarkReport report) {
        benchmarkPanel.showReport(report);
        contentTabs.setSelectedIndex(3);
    }

    public void resetBenchmarkProgress() {
        benchmarkPanel.resetProgress();
    }

    public void updateBenchmarkProgress(BenchmarkProgressSnapshot snapshot) {
        benchmarkPanel.updateProgress(snapshot);
        contentTabs.setSelectedIndex(3);
    }

    public void showStatus(String message) {
        statusLabel.setText(message);
    }

    public void showError(String message) {
        statusLabel.setText(message);
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public void refreshFileExplorer() {
        fileBrowserPanel.refreshCurrentDirectory();
    }

    private void configureTextAreas() {
        statsArea.setEditable(false);
        statsArea.setLineWrap(true);
        statsArea.setWrapStyleWord(true);
        statsArea.setBackground(PANEL_BACKGROUND);
        statsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        filesHintArea.setEditable(false);
        filesHintArea.setLineWrap(true);
        filesHintArea.setWrapStyleWord(true);
        filesHintArea.setOpaque(false);
        filesHintArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        filesHintArea.setBorder(null);
    }

    private void configureTables() {
        symbolTable.setFillsViewportHeight(true);
        symbolTable.setRowHeight(24);
        symbolTable.getTableHeader().setReorderingAllowed(false);
    }

    private void installInputAutoSync() {
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                syncFromInputField();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                syncFromInputField();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                syncFromInputField();
            }
        });
    }

    private JPanel createCenterPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new EmptyBorder(12, 12, 12, 12));
        center.setBackground(FRAME_BACKGROUND);

        contentTabs.addTab("Fitxers", createFilesTab());
        contentTabs.addTab("Taula de codis", wrapPanel("Codis Huffman assignats", new JScrollPane(symbolTable)));
        contentTabs.addTab("Arbre", createTreeTab());
        contentTabs.addTab("Comparatives", benchmarkPanel);

        center.add(contentTabs, BorderLayout.CENTER);
        return center;
    }

    private JPanel createFilesTab() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(FRAME_BACKGROUND);

        JPanel workspace = new JPanel();
        workspace.setLayout(new BoxLayout(workspace, BoxLayout.Y_AXIS));
        workspace.setBackground(PANEL_BACKGROUND);
        workspace.setBorder(createSectionBorder("Notes i navegador de fitxers"));

        JLabel headline = new JLabel("Comenca aqui");
        headline.setFont(headline.getFont().deriveFont(Font.BOLD, 20f));
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel selectorsPanel = new JPanel(new GridBagLayout());
        selectorsPanel.setOpaque(false);
        selectorsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectorsPanel.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(new Color(205, 213, 224)),
            new EmptyBorder(14, 14, 14, 14)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.weightx = 0.0;
        selectorsPanel.add(new JLabel("Fitxer d'entrada:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        selectorsPanel.add(inputField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        selectorsPanel.add(browseInputButton, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        selectorsPanel.add(new JLabel("Fitxer de sortida:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        selectorsPanel.add(outputField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        selectorsPanel.add(browseOutputButton, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        selectorsPanel.add(new JLabel("Estrategia de cua:"), gbc);

        gbc.gridx = 1;
        selectorsPanel.add(queueCombo, gbc);

        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.EAST;
        selectorsPanel.add(processButton, gbc);

        JPanel statsPanel = wrapPanel("Estadistiques", new JScrollPane(statsArea));
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsPanel.setPreferredSize(new Dimension(380, 270));

        workspace.add(headline);
        workspace.add(Box.createVerticalStrut(8));
        filesHintArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        workspace.add(filesHintArea);
        workspace.add(Box.createVerticalStrut(14));
        workspace.add(selectorsPanel);
        workspace.add(Box.createVerticalStrut(14));
        workspace.add(statsPanel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileBrowserPanel, workspace);
        splitPane.setResizeWeight(0.68);
        splitPane.setBorder(null);
        splitPane.setContinuousLayout(true);
        root.add(splitPane, BorderLayout.CENTER);
        return root;
    }

    private JPanel createTreeTab() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(FRAME_BACKGROUND);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setBackground(FRAME_BACKGROUND);
        JButton zoomInButton = new JButton("Amplia");
        JButton zoomOutButton = new JButton("Redueix");
        JButton resetViewButton = new JButton("Reinicia vista");
        JLabel hint = new JLabel("Arrossega per moure l'arbre. Usa la roda del ratoli o els botons per fer zoom.");
        hint.setForeground(new Color(76, 88, 106));

        zoomInButton.addActionListener(event -> treePanel.zoomIn());
        zoomOutButton.addActionListener(event -> treePanel.zoomOut());
        resetViewButton.addActionListener(event -> treePanel.resetView());

        controls.add(zoomInButton);
        controls.add(zoomOutButton);
        controls.add(resetViewButton);
        controls.add(Box.createHorizontalStrut(12));
        controls.add(hint);

        JPanel panel = wrapPanel("Arbre de Huffman", treePanel);
        root.add(controls, BorderLayout.NORTH);
        root.add(panel, BorderLayout.CENTER);
        return root;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 6));
        panel.setBackground(PANEL_BACKGROUND);
        panel.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(189, 198, 210)),
            new EmptyBorder(10, 12, 10, 12)
        ));

        JPanel progressPanel = new JPanel();
        progressPanel.setOpaque(false);
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressTextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressTextLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setStringPainted(false);
        progressPanel.add(progressTextLabel);
        progressPanel.add(progressBar);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        infoPanel.setOpaque(false);
        infoPanel.add(phaseLabel);
        infoPanel.add(new JLabel("|"));
        infoPanel.add(etaLabel);

        panel.add(progressPanel, BorderLayout.CENTER);
        panel.add(infoPanel, BorderLayout.WEST);
        panel.add(statusLabel, BorderLayout.EAST);
        return panel;
    }

    private JPanel wrapPanel(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL_BACKGROUND);
        panel.setBorder(createSectionBorder(title));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private CompoundBorder createSectionBorder(String title) {
        return new CompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(189, 198, 210)), title),
            new EmptyBorder(6, 6, 6, 6)
        );
    }

    private void installActions() {
        browseInputButton.addActionListener(event -> applyExplorerSelectionToInput());
        browseOutputButton.addActionListener(event -> applyExplorerSelectionToOutput());
        processButton.addActionListener(event -> dispatchPrimaryAction());
        benchmarkPanel.addRunListener(event -> dispatchBenchmark());
    }

    private void applyExplorerSelectionToInput() {
        Path selectedPath = fileBrowserPanel.selectedPath();
        if (selectedPath == null) {
            showError("Selecciona abans un fitxer a l'explorador incrustat.");
            return;
        }
        applyBrowserPathToInput(selectedPath);
    }

    private void applyExplorerSelectionToOutput() {
        Path selectedPath = fileBrowserPanel.selectedPath();
        if (selectedPath == null) {
            showError("Selecciona abans un fitxer a l'explorador incrustat.");
            return;
        }

        outputField.setText(selectedPath.toString());
        contentTabs.setSelectedIndex(0);
        showStatus("S'ha seleccionat la ruta de sortida: " + selectedPath.getFileName());
    }

    private void dispatchPrimaryAction() {
        if (viewListener == null) {
            return;
        }
        Path inputPath = parsePath(inputField.getText(), "entrada");
        Path outputPath = parsePath(outputField.getText(), "sortida");
        if (inputPath == null || outputPath == null) {
            return;
        }

        PriorityQueueStrategy strategy = (PriorityQueueStrategy) queueCombo.getSelectedItem();
        if (isArchiveInput(inputField.getText())) {
            viewListener.onDecompressRequested(inputPath, outputPath, strategy);
        } else {
            viewListener.onCompressRequested(inputPath, outputPath, strategy);
        }
    }

    private void applyBrowserPathToInput(Path selectedPath) {
        inputField.setText(selectedPath.toString());
        contentTabs.setSelectedIndex(0);
        showStatus("S'ha seleccionat el fitxer d'entrada: " + selectedPath.getFileName());
    }

    private void dispatchBenchmark() {
        if (viewListener == null) {
            return;
        }

        try {
            BenchmarkConfig config = benchmarkPanel.readConfig();
            viewListener.onBenchmarkRequested(config);
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void syncFromInputField() {
        String inputText = inputField.getText();
        outputField.setText(derivedOutputText(inputText));
        updateProcessButtonLabel();
    }

    private void updateProcessButtonLabel() {
        if (isArchiveInput(inputField.getText())) {
            processButton.setText("Extreu arxiu");
        } else {
            processButton.setText("Comprimeix a .hff");
        }
    }

    private String derivedOutputText(String inputText) {
        String trimmed = inputText == null ? "" : inputText.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (isArchiveInput(trimmed)) {
            return trimmed.substring(0, trimmed.length() - 4);
        }
        return trimmed + ".hff";
    }

    private boolean isArchiveInput(String text) {
        return text != null && text.trim().toLowerCase().endsWith(".hff");
    }

    private Path parsePath(String text, String label) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            showError("Selecciona abans una ruta de " + label + ".");
            return null;
        }
        try {
            return Path.of(trimmed);
        } catch (InvalidPathException exception) {
            showError("La ruta de " + label + " no es valida: " + exception.getInput());
            return null;
        }
    }

    private void populateSymbols(List<HuffmanSymbolInfo> symbols) {
        symbolTableModel.setRowCount(0);
        for (HuffmanSymbolInfo symbol : symbols) {
            symbolTableModel.addRow(new Object[] {
                formatSymbol(symbol.symbol()),
                formatHex(symbol.symbol()),
                symbol.frequency(),
                formatProbability(symbol.probability()),
                symbol.code().isEmpty() ? "<buit>" : symbol.code()
            });
        }
    }

    private String formatPhase(ProgressSnapshot snapshot) {
        return switch (snapshot.phase()) {
            case ANALYZING -> "Analitzant";
            case COMPRESSING -> "Comprimint";
            case DECOMPRESSING -> "Descomprimint";
        };
    }

    private String formatBytes(long bytes) {
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        if (unitIndex == 0) {
            return bytes + " " + units[unitIndex];
        }
        return SIZE_FORMAT.format(value) + " " + units[unitIndex];
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatDecimal(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    private String formatProbability(double probability) {
        return DECIMAL_FORMAT.format(probability);
    }

    private String formatSymbol(int symbol) {
        if (symbol > 0xFF) {
            return String.format("parella[%02X %02X]", (symbol >>> 8) & 0xFF, symbol & 0xFF);
        }
        return switch (symbol) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case ' ' -> "<espai>";
            default -> symbol >= 32 && symbol <= 126
                ? Character.toString((char) symbol)
                : String.format("byte[%d]", symbol);
        };
    }

    private String formatHex(int symbol) {
        if (symbol <= 0xFF) {
            return String.format("0x%02X", symbol);
        }
        return String.format("0x%04X", symbol);
    }

}
