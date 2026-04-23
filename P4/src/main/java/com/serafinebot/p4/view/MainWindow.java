package com.serafinebot.p4.view;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.benchmark.BenchmarkConfig;
import com.serafinebot.p4.model.benchmark.BenchmarkProgressSnapshot;
import com.serafinebot.p4.model.benchmark.BenchmarkReport;
import com.serafinebot.p4.model.progress.ProgressSnapshot;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.info.BlockInfo;
import com.serafinebot.p4.model.info.CompressionInfo;
import com.serafinebot.p4.model.info.CompressionStats;
import com.serafinebot.p4.model.info.DecompressionInfo;
import com.serafinebot.p4.model.info.DecompressionStats;
import com.serafinebot.p4.model.info.HuffmanSymbolInfo;
import com.serafinebot.p4.model.info.HuffmanTreeNodeInfo;
import com.serafinebot.p4.util.ByteFormat;

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

    private static final int FILES_TAB_INDEX = 0;
    private static final Color FRAME_BACKGROUND = new Color(238, 241, 245);
    private static final Color PANEL_BACKGROUND = new Color(248, 250, 252);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.000000");

    private final JTextField inputField = new JTextField(42);
    private final JTextField outputField = new JTextField(42);
    private final JButton processButton = new JButton("Comprimeix a .hff");
    private final JComboBox<PriorityQueueStrategy> queueCombo = new JComboBox<>();
    private final JComboBox<CompressionMode> modeCombo = new JComboBox<>();
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
    private final BlockListPanel blockListPanel = new BlockListPanel();
    private final BlockListPanel codeBlockListPanel = new BlockListPanel();
    private final BenchmarkPanel benchmarkPanel = new BenchmarkPanel();
    private final FileBrowserPanel fileBrowserPanel = new FileBrowserPanel();
    private final JLabel progressTextLabel = new JLabel("Inactiu • 0% (0 B / 0 B)");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel phaseLabel = new JLabel("Fase: Inactiu");
    private final JLabel etaLabel = new JLabel("Temps restant --");
    private final JLabel statusLabel = new JLabel("Preparat.", SwingConstants.RIGHT);
    private final JTabbedPane contentTabs = new JTabbedPane();
    private final JPanel progressPanel = new JPanel();
    private final JPanel progressInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));

    private ViewListener viewListener;

    public MainWindow() {
        super("P4 Compressor Huffman");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        getContentPane().setBackground(FRAME_BACKGROUND);
        setLayout(new BorderLayout());

        queueCombo.setModel(new DefaultComboBoxModel<>(PriorityQueueStrategy.values()));
        modeCombo.setModel(new DefaultComboBoxModel<>(CompressionMode.requestModes()));
        configureTextAreas();
        configureTables();
        installInputAutoSync();
        fileBrowserPanel.setFileActivationListener(this::applyBrowserPathToInput);

        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        clearResults();
        installActions();
        updateBottomPanelsVisibility();

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
        processButton.setEnabled(!running);
        queueCombo.setEnabled(!running);
        modeCombo.setEnabled(!running);
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
            ByteFormat.format(snapshot.processedBytes()),
            ByteFormat.format(snapshot.totalBytes())));
        phaseLabel.setText("Fase: " + formatPhase(snapshot));
        etaLabel.setText(snapshot.estimatedRemainingMillis() < 0L ? "Temps restant --" : "Temps restant " + formatDuration(snapshot.estimatedRemainingMillis()));
    }

    public void showCompressionInfo(CompressionInfo info) {
        CompressionStats stats = info.stats();
        StringBuilder str = new StringBuilder();
        str.append("Operacio: Compressio\n");
        str.append("Estrategia: ").append(formatMode(stats.mode(), info.blocks())).append('\n');
        str.append("Cua: ").append(stats.priorityQueueStrategy()).append('\n');
        str.append("Mida original: ").append(stats.originalSize()).append(" bytes\n");
        str.append("Mida de l'arxiu: ").append(stats.archiveSize()).append(" bytes\n");
        str.append("Mida de la capcalera: ").append(stats.overheadSize()).append(" bytes\n");
        str.append("Mida de la carrega: ").append(stats.payloadSize()).append(" bytes\n");
        str.append("Simbols diferents: ").append(stats.distinctSymbolCount()).append('\n');
        str.append("Entropia: ").append(formatDecimal(stats.entropy())).append(" bits/simbol\n");
        str.append("Longitud mitjana Huffman: ").append(formatDecimal(stats.averageHuffmanCodeLength())).append(" bits/simbol\n");
        str.append("Ratio de compressio: ").append(formatDecimal(compressionRatio(stats))).append(" : 1\n");
        str.append("Temps: ").append(stats.elapsedMillis()).append(" ms");
        appendBlockBreakdown(str, stats.mode(), info.blocks());
        statsArea.setText(str.toString());
        applyReportContent(info.symbols(), info.tree(), info.blocks(),
            "No hi ha cap arbre de Huffman disponible.",
            stats.mode() == CompressionMode.HUFFMAN_2_BYTE);
        contentTabs.setSelectedIndex(0);
    }

    public void showDecompressionInfo(DecompressionInfo info) {
        DecompressionStats stats = info.stats();
        StringBuilder str = new StringBuilder();
        str.append("Operacio: Descompressio\n");
        str.append("Estrategia: ").append(formatMode(stats.mode(), info.blocks())).append('\n');
        str.append("Mida de l'arxiu: ").append(stats.archiveSize()).append(" bytes\n");
        str.append("Mida restaurada: ").append(stats.restoredSize()).append(" bytes\n");
        str.append("Temps: ").append(stats.elapsedMillis()).append(" ms");
        appendBlockBreakdown(str, stats.mode(), info.blocks());
        statsArea.setText(str.toString());
        applyReportContent(info.symbols(), info.tree(), info.blocks(),
            "Arxiu en mode emmagatzemat: no hi ha cap arbre de Huffman disponible.",
            stats.mode() == CompressionMode.HUFFMAN_2_BYTE);
        contentTabs.setSelectedIndex(0);
    }

    private double compressionRatio(CompressionStats stats) {
        if (stats.archiveSize() <= 0L) {
            return 0.0;
        }
        return stats.originalSize() / (double) stats.archiveSize();
    }

    private String formatMode(CompressionMode mode, List<BlockInfo> blocks) {
        if (mode != CompressionMode.HUFFMAN_BLOCK || blocks == null || blocks.isEmpty()) {
            return mode.toString();
        }
        int byteBlocks = 0;
        int wordBlocks = 0;
        for (BlockInfo block : blocks) {
            if (block.mode() == CompressionMode.HUFFMAN_1_BYTE) {
                byteBlocks++;
            } else if (block.mode() == CompressionMode.HUFFMAN_2_BYTE) {
                wordBlocks++;
            }
        }
        if (byteBlocks > 0 && wordBlocks == 0) {
            return mode + " (1 byte)";
        }
        if (wordBlocks > 0 && byteBlocks == 0) {
            return mode + " (2 bytes)";
        }
        if (wordBlocks > 0) {
            return mode + " (mixt 1+2 bytes)";
        }
        return mode.toString();
    }

    private void appendBlockBreakdown(StringBuilder stats, CompressionMode mode, List<BlockInfo> blocks) {
        if (mode != CompressionMode.HUFFMAN_BLOCK || blocks == null || blocks.isEmpty()) {
            return;
        }
        int byteBlocks = 0;
        int wordBlocks = 0;
        int storedBlocks = 0;
        for (BlockInfo block : blocks) {
            switch (block.mode()) {
                case HUFFMAN_1_BYTE -> byteBlocks++;
                case HUFFMAN_2_BYTE -> wordBlocks++;
                case STORED -> storedBlocks++;
                default -> { }
            }
        }
        stats.append('\n').append("Blocs: ").append(blocks.size())
            .append(" (1 byte: ").append(byteBlocks)
            .append(", 2 bytes: ").append(wordBlocks)
            .append(", emmagatzemat: ").append(storedBlocks).append(')');
    }

    private void applyReportContent(List<HuffmanSymbolInfo> symbols,
                                    HuffmanTreeNodeInfo tree,
                                    List<BlockInfo> blocks,
                                    String emptyTreeMessage,
                                    boolean wordMode) {
        boolean hasBlocks = blocks != null && !blocks.isEmpty();
        blockListPanel.setVisible(hasBlocks);
        codeBlockListPanel.setVisible(hasBlocks);
        if (hasBlocks) {
            blockListPanel.setBlocks(blocks);
            codeBlockListPanel.setBlocks(blocks);
        } else {
            blockListPanel.clear();
            codeBlockListPanel.clear();
            populateSymbols(symbols, wordMode);
            treePanel.setTree(tree, emptyTreeMessage, wordMode);
        }
    }

    public void clearResults() {
        statsArea.setText("Encara no s'ha executat cap operacio. Fes doble clic damunt un fitxer de l'explorador per carregar-lo a la ruta d'entrada. La ruta de sortida es deriva automaticament de l'entrada, pero la pots editar manualment despres.");
        symbolTableModel.setRowCount(0);
        treePanel.setTree(null, "No hi ha cap arbre de Huffman disponible.");
        blockListPanel.clear();
        blockListPanel.setVisible(false);
        codeBlockListPanel.clear();
        codeBlockListPanel.setVisible(false);
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
        if (!isFilesTabSelected()) {
            return;
        }
        statusLabel.setText(message);
    }

    public void showError(String message) {
        if (isFilesTabSelected()) {
            statusLabel.setText(message);
        }
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
        contentTabs.addTab("Taula de codis", createCodeTableTab());
        contentTabs.addTab("Arbre", createTreeTab());
        contentTabs.addTab("Comparatives", benchmarkPanel);
        contentTabs.addChangeListener(event -> updateBottomPanelsVisibility());

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

        gbc.gridy = 1;
        gbc.gridx = 0;
        selectorsPanel.add(new JLabel("Fitxer de sortida:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        selectorsPanel.add(outputField, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        selectorsPanel.add(new JLabel("Estrategia de cua:"), gbc);

        gbc.gridx = 1;
        selectorsPanel.add(queueCombo, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        selectorsPanel.add(new JLabel("Mode de compressio:"), gbc);

        gbc.gridx = 1;
        selectorsPanel.add(modeCombo, gbc);

        gbc.gridy = 4;
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
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

    private JPanel createCodeTableTab() {
        JPanel root = new JPanel(new BorderLayout(10, 0));
        root.setBackground(FRAME_BACKGROUND);

        codeBlockListPanel.setSelectionListener(block -> {
            renderBlock(block);
            blockListPanel.setSelectedIndex(codeBlockListPanel.getSelectedIndex());
        });

        JPanel table = wrapPanel("Codis Huffman assignats", new JScrollPane(symbolTable));
        root.add(codeBlockListPanel, BorderLayout.WEST);
        root.add(table, BorderLayout.CENTER);
        return root;
    }

    private void renderBlock(BlockInfo block) {
        boolean wordMode = block.mode() == CompressionMode.HUFFMAN_2_BYTE;
        populateSymbols(block.symbols(), wordMode);
        treePanel.setTree(block.tree(), "Aquest bloc no te arbre de Huffman (mode emmagatzemat).", wordMode);
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

        JPanel treeSection = wrapPanel("Arbre de Huffman", treePanel);

        blockListPanel.setSelectionListener(block -> {
            renderBlock(block);
            codeBlockListPanel.setSelectedIndex(blockListPanel.getSelectedIndex());
        });

        JPanel content = new JPanel(new BorderLayout(10, 0));
        content.setBackground(FRAME_BACKGROUND);
        content.add(blockListPanel, BorderLayout.WEST);
        content.add(treeSection, BorderLayout.CENTER);

        root.add(controls, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 6));
        panel.setBackground(PANEL_BACKGROUND);
        panel.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(189, 198, 210)),
            new EmptyBorder(10, 12, 10, 12)
        ));

        progressPanel.setOpaque(false);
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
        progressTextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressTextLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setStringPainted(false);
        progressPanel.add(progressTextLabel);
        progressPanel.add(progressBar);

        progressInfoPanel.setOpaque(false);
        progressInfoPanel.add(phaseLabel);
        progressInfoPanel.add(new JLabel("|"));
        progressInfoPanel.add(etaLabel);

        panel.add(progressPanel, BorderLayout.CENTER);
        panel.add(progressInfoPanel, BorderLayout.WEST);
        panel.add(statusLabel, BorderLayout.EAST);
        return panel;
    }

    private void updateBottomPanelsVisibility() {
        boolean showFilePanels = isFilesTabSelected();
        progressPanel.setVisible(showFilePanels);
        progressInfoPanel.setVisible(showFilePanels);
    }

    private boolean isFilesTabSelected() {
        return contentTabs.getSelectedIndex() == FILES_TAB_INDEX;
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
        processButton.addActionListener(event -> dispatchPrimaryAction());
        benchmarkPanel.addRunListener(event -> dispatchBenchmark());
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
            CompressionMode requestedMode = (CompressionMode) modeCombo.getSelectedItem();
            viewListener.onCompressRequested(inputPath, outputPath, strategy, requestedMode);
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

    private void populateSymbols(List<HuffmanSymbolInfo> symbols, boolean wordMode) {
        symbolTableModel.setRowCount(0);
        for (HuffmanSymbolInfo symbol : symbols) {
            symbolTableModel.addRow(new Object[] {
                formatSymbol(symbol.symbol(), wordMode),
                formatHex(symbol.symbol(), wordMode),
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

    private String formatSymbol(int symbol, boolean wordMode) {
        if (wordMode) {
            return String.format("parella[%02X %02X]", (symbol >>> 8) & 0xFF, symbol & 0xFF);
        }
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

    private String formatHex(int symbol, boolean wordMode) {
        if (wordMode) {
            return String.format("0x%04X", symbol & 0xFFFF);
        }
        if (symbol <= 0xFF) {
            return String.format("0x%02X", symbol);
        }
        return String.format("0x%04X", symbol);
    }

}
