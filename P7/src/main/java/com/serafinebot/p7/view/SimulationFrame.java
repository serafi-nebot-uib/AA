package com.serafinebot.p7.view;

import com.serafinebot.p7.model.Game;
import com.serafinebot.p7.model.RuleVariant;
import com.serafinebot.p7.model.SimulationResult;
import com.serafinebot.p7.model.SimulationStats;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Swing implementation of the P7 simulation view.
 *
 * <p>The frame is deliberately presentational. It collects configuration values,
 * emits a single listener callback when the user clicks Simular, and renders the
 * {@link SimulationResult} supplied by the controller. It does not construct
 * simulators or apply game rules.</p>
 */
public final class SimulationFrame extends JFrame implements SimulationView {

    private static final Color BACKGROUND = new Color(244, 246, 250);
    private static final Color CARD = Color.WHITE;
    private static final Color INK = new Color(31, 41, 55);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final int MAX_THREADS = Math.max(1, Math.min(64, Runtime.getRuntime().availableProcessors()));

    private final JSpinner games = new JSpinner(new SpinnerNumberModel(10_000, 1, 10_000_000, 1_000));
    private final JSpinner playerCount = new JSpinner(new SpinnerNumberModel(1, Game.MIN_PLAYERS, Game.MAX_PLAYERS, 1));
    private final JComboBox<RuleVariant> variant = new JComboBox<>(RuleVariant.values());
    private final JSpinner threads = new JSpinner(new SpinnerNumberModel(defaultThreadCount(), 1, MAX_THREADS, 1));
    private final JCheckBox compareVariants = new JCheckBox("Comparar variants");
    private final JTextField seed = new JTextField();
    private final JButton randomSeed = new JButton("Nova llavor");
    private final JButton run = new JButton("Simular");
    private final JLabel status = new JLabel("Introdueix N i executa una simulacio.");
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea requiredOutput = new JTextArea(13, 22);
    private final JTextArea visitOutput = new JTextArea(13, 26);
    private final JTextArea comparisonOutput = new JTextArea(13, 26);
    private final JTextArea victoryOutput = new JTextArea(13, 26);
    private final HistogramPanel histogram = new HistogramPanel();
    private final Map<String, JLabel> statValues = new LinkedHashMap<>();
    private SimulationViewListener listener;

    public SimulationFrame() {
        super("P7 - Juego de la Oca Monte Carlo");
        configureWindow();
        configureActions();
        clearStats();
    }

    /**
     * Stores the controller listener that receives user actions.
     */
    @Override
    public void setListener(SimulationViewListener listener) {
        this.listener = listener;
    }

    /**
     * Disables input and shows an indeterminate progress bar while the controller
     * runs a background simulation.
     */
    @Override
    public void setRunning(boolean running) {
        games.setEnabled(!running);
        playerCount.setEnabled(!running);
        variant.setEnabled(!running);
        threads.setEnabled(!running);
        compareVariants.setEnabled(!running);
        seed.setEnabled(!running);
        randomSeed.setEnabled(!running);
        run.setEnabled(!running);
        progress.setIndeterminate(running);
        progress.setVisible(running);
        if (running) {
            status.setText("Simulant partides...");
        }
    }

    /**
     * Renders all result views: statistics card, required text output, square
     * frequencies, variant comparison and histogram.
     */
    @Override
    public void showResult(SimulationResult result) {
        SimulationStats stats = result.stats();
        statValues.get("minimum").setText(Integer.toString(stats.minimum()));
        statValues.get("p1").setText(Integer.toString(stats.p1()));
        statValues.get("p2").setText(Integer.toString(stats.p2()));
        statValues.get("p3").setText(Integer.toString(stats.p3()));
        statValues.get("p4").setText(Integer.toString(stats.p4()));
        statValues.get("median").setText(Integer.toString(stats.median()));
        statValues.get("p6").setText(Integer.toString(stats.p6()));
        statValues.get("p7").setText(Integer.toString(stats.p7()));
        statValues.get("p8").setText(Integer.toString(stats.p8()));
        statValues.get("p9").setText(Integer.toString(stats.p9()));
        statValues.get("maximum").setText(Integer.toString(stats.maximum()));
        statValues.get("mean").setText(String.format(Locale.US, "%.6f", stats.mean()));
        requiredOutput.setText(stats.formatRequiredOutput());
        requiredOutput.setCaretPosition(0);
        visitOutput.setText(formatSquareVisits(result.squareVisits()));
        visitOutput.setCaretPosition(0);
        comparisonOutput.setText(formatComparison(result.comparisonStats()));
        comparisonOutput.setCaretPosition(0);
        victoryOutput.setText(formatVictories(result.winnerCounts()));
        victoryOutput.setCaretPosition(0);
        histogram.setObservations(result.turnCounts());
        status.setText("N=" + result.turnCounts().length + ", variant=" + result.variant().displayName()
                + ", jugadors=" + result.playerCount() + ", fils=" + result.threadCount() + ", llavor=" + result.seed()
                + ", temps=" + result.elapsedMillis() + " ms.");
    }

    /**
     * Displays a user-visible error and mirrors it in the status line.
     */
    @Override
    public void showError(String message) {
        status.setText(message);
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /** Builds the fixed window shell and top-level layout. */
    private void configureWindow() {
        getContentPane().setBackground(BACKGROUND);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        add(controlPanel(), BorderLayout.NORTH);
        add(contentPanel(), BorderLayout.CENTER);
        add(statusPanel(), BorderLayout.SOUTH);
        pack();
        setMinimumSize(new Dimension(980, 620));
        setLocationRelativeTo(null);
    }

    /** Wires Swing events to MVC listener callbacks. */
    private void configureActions() {
        run.addActionListener(event -> {
            if (listener != null) {
                listener.onRunRequested((Integer) games.getValue(), seed.getText(),
                        (RuleVariant) variant.getSelectedItem(), (Integer) threads.getValue(),
                        (Integer) playerCount.getValue(), compareVariants.isSelected());
            }
        });
        randomSeed.addActionListener(event -> seed.setText(Long.toString(System.nanoTime())));
    }

    /** Creates the top configuration area. */
    private JPanel controlPanel() {
        JPanel panel = card(new BorderLayout(12, 12), "Configuracio");
        JLabel hint = new JLabel("Simula N partides, compara variants i pot repartir el calcul entre diversos fils.");
        hint.setForeground(MUTED);

        JPanel fields = new JPanel(new GridLayout(2, 4, 10, 10));
        fields.setOpaque(false);
        fields.add(fieldCard("Partides (N)", games));
        fields.add(fieldCard("Jugadors", playerCount));
        fields.add(fieldCard("Variant de regles", variant));
        fields.add(fieldCard("Fils de simulacio", threads));
        fields.add(seedPanel());
        fields.add(comparePanel());
        fields.add(actionPanel());

        panel.add(hint, BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);
        return panel;
    }

    private JPanel seedPanel() {
        JPanel panel = fieldCard("Llavor aleatoria (opcional)", seed);
        panel.add(randomSeed, BorderLayout.EAST);
        return panel;
    }

    private JPanel actionPanel() {
        JPanel panel = fieldCard("Execucio", run);
        progress.setVisible(false);
        progress.setStringPainted(false);
        panel.add(progress, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel comparePanel() {
        compareVariants.setOpaque(false);
        return fieldCard("Comparacio opcional", compareVariants);
    }

    private JPanel contentPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.add(statsPanel(), BorderLayout.WEST);
        panel.add(histogramCard(), BorderLayout.CENTER);
        panel.add(outputPanel(), BorderLayout.EAST);
        return panel;
    }

    private JPanel statsPanel() {
        JPanel panel = card(new GridBagLayout(), "Estadistics");
        panel.setPreferredSize(new Dimension(260, 460));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);

        addStat(panel, c, "minimum", "Minimo");
        addStat(panel, c, "p1", "P1 (0.1)");
        addStat(panel, c, "p2", "P2 (0.2)");
        addStat(panel, c, "p3", "P3 (0.3)");
        addStat(panel, c, "p4", "P4 (0.4)");
        addStat(panel, c, "median", "Mediana");
        addStat(panel, c, "p6", "P6 (0.6)");
        addStat(panel, c, "p7", "P7 (0.7)");
        addStat(panel, c, "p8", "P8 (0.8)");
        addStat(panel, c, "p9", "P9 (0.9)");
        addStat(panel, c, "maximum", "Maximo");
        addStat(panel, c, "mean", "Media");
        c.weighty = 1.0;
        panel.add(new JLabel(), c);
        return panel;
    }

    private void addStat(JPanel panel, GridBagConstraints c, String key, String label) {
        JPanel row = new JPanel(new BorderLayout(8, 8));
        row.setOpaque(false);
        JLabel name = new JLabel(label);
        name.setForeground(MUTED);
        JLabel value = new JLabel("-");
        value.setFont(value.getFont().deriveFont(Font.BOLD));
        value.setForeground(INK);
        row.add(name, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        statValues.put(key, value);
        panel.add(row, c);
        c.gridy++;
    }

    private JPanel histogramCard() {
        JPanel panel = card(new BorderLayout(8, 8), "Distribucio empirica");
        JLabel hint = new JLabel("Histograma dels torns observats en la simulacio.");
        hint.setForeground(MUTED);
        panel.add(hint, BorderLayout.NORTH);
        panel.add(histogram, BorderLayout.CENTER);
        return panel;
    }

    private JPanel outputPanel() {
        JPanel panel = card(new BorderLayout(8, 8), "Detall numeric");
        panel.setPreferredSize(new Dimension(330, 460));
        configureTextArea(requiredOutput);
        configureTextArea(visitOutput);
        configureTextArea(comparisonOutput);
        configureTextArea(victoryOutput);
        requiredOutput.setText("Minimo: ...\nP1: ...\nP2: ...\nP3: ...\nP4: ...\nMediana: ...\nP6: ...\nP7: ...\nP8: ...\nP9: ...\nMaximo: ...\nMedia: ...\n");
        visitOutput.setText("Executa una simulacio per veure les frequencies de casella.\n");
        comparisonOutput.setText("Activa 'Comparar variants' per veure la comparacio.\n");
        victoryOutput.setText("Executa una simulacio per veure probabilitats de victoria.\n");

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Sortida", new JScrollPane(requiredOutput));
        tabs.addTab("Caselles", new JScrollPane(visitOutput));
        tabs.addTab("Variants", new JScrollPane(comparisonOutput));
        tabs.addTab("Victories", new JScrollPane(victoryOutput));
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel statusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        status.setForeground(MUTED);
        panel.add(status, BorderLayout.CENTER);
        return panel;
    }

    private JPanel fieldCard(String title, java.awt.Component component) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(new Color(249, 250, 251));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JPanel card(LayoutManager layout, String title) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                BorderFactory.createTitledBorder(title)
        ));
        return panel;
    }

    private void clearStats() {
        for (JLabel value : statValues.values()) {
            value.setText("-");
        }
    }

    private static void configureTextArea(JTextArea textArea) {
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
    }

    private static int defaultThreadCount() {
        return Math.max(1, MAX_THREADS - 1);
    }

    /**
     * Formats visit frequencies as a monospaced table for the Caselles tab.
     */
    private static String formatSquareVisits(long[] visits) {
        long total = 0L;
        for (long visit : visits) {
            total += visit;
        }
        if (total == 0L) {
            return "No hi ha visites registrades.\n";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.US, "%6s %14s %10s%n", "Cas.", "Visites", "%"));
        builder.append(String.format(Locale.US, "%6s %14s %10s%n", "----", "-------", "----"));
        for (int square = 0; square < visits.length; square++) {
            double percentage = visits[square] * 100.0 / total;
            builder.append(String.format(Locale.US, "%6d %14d %9.3f%%%n", square, visits[square], percentage));
        }
        return builder.toString();
    }

    /**
     * Formats optional rule-variant comparisons as a compact monospaced table.
     */
    private static String formatComparison(Map<RuleVariant, SimulationStats> comparisonStats) {
        if (comparisonStats.isEmpty()) {
            return "Activa 'Comparar variants' per simular totes les variants amb la mateixa N i llavor.\n";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.US, "%-26s %5s %5s %5s %10s %5s%n",
                "Variant", "Min", "Med", "P9", "Media", "Max"));
        builder.append(String.format(Locale.US, "%-26s %5s %5s %5s %10s %5s%n",
                "-------", "---", "---", "--", "-----", "---"));
        for (Map.Entry<RuleVariant, SimulationStats> entry : comparisonStats.entrySet()) {
            SimulationStats stats = entry.getValue();
            builder.append(String.format(Locale.US, "%-26s %5d %5d %5d %10.3f %5d%n",
                    abbreviate(entry.getKey().displayName(), 26), stats.minimum(), stats.median(),
                    stats.p9(), stats.mean(), stats.maximum()));
        }
        return builder.toString();
    }

    private static String formatVictories(int[] winnerCounts) {
        int total = 0;
        for (int wins : winnerCounts) {
            total += wins;
        }
        if (total == 0) {
            return "No hi ha victories registrades.\n";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.US, "%8s %12s %12s%n", "Jugador", "Victories", "Prob."));
        builder.append(String.format(Locale.US, "%8s %12s %12s%n", "-------", "---------", "-----"));
        for (int player = 0; player < winnerCounts.length; player++) {
            builder.append(String.format(Locale.US, "%8d %12d %11.6f%n",
                    player + 1, winnerCounts[player], winnerCounts[player] / (double) total));
        }
        return builder.toString();
    }

    private static String abbreviate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
