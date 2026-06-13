package com.serafinebot.p5.view;

import com.serafinebot.p5.model.GameInput;
import com.serafinebot.p5.model.GameResult;
import com.serafinebot.p5.model.KeypadMode;
import com.serafinebot.p5.model.ReplayStep;
import com.serafinebot.p5.model.SolverMode;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;

/**
 * Main Swing view for configuring and solving calculator-game positions.
 *
 * <p>The frame contains no game rules and no concrete controller reference. It
 * collects user input, notifies a {@link GameViewListener} when the input
 * changes, and renders the {@link GameResult} supplied by the controller. Replay
 * navigation is purely presentational: it pages through the already-computed
 * {@link ReplayStep} list.
 */
public final class GameFrame extends JFrame implements GameView {

    private static final Color BACKGROUND = new Color(244, 246, 250);
    private static final Color CARD = Color.WHITE;
    private static final Color INK = new Color(31, 41, 55);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color SUCCESS = new Color(22, 163, 74);
    private static final Color DANGER = new Color(220, 38, 38);
    private static final Color WARNING = new Color(245, 158, 11);
    private static final Color KEY = new Color(229, 231, 235);
    private static final Color KEY_LEGAL = new Color(219, 234, 254);
    private static final Color KEY_LAST = new Color(254, 243, 199);

    private final JSpinner width = spinner(3, 2, 5);
    private final JSpinner height = spinner(3, 2, 5);
    private final JSpinner initialTotal = spinner(0, 0, 999);
    private final JSpinner limit = spinner(31, 1, 1000);
    private final JSpinner lastPlayed = spinner(0, 0, 25);
    private final JSpinner playerCount = spinner(2, 2, 20);
    private final JSpinner startingPlayer = spinner(1, 1, 20);
    private final JComboBox<String> keypadMode = new JComboBox<>(new String[]{"Estandard", "Aleatori"});
    private final JComboBox<String> solverMode = new JComboBox<>(new String[]{"DP top-down", "DP bottom-up"});
    private final JButton regenerateKeypad = new JButton("Nou aleatori");
    private final JPanel keypadPanel = new JPanel();
    private final JLabel status = new JLabel(" ");
    private final JLabel loserValue = new JLabel("-");
    private final JLabel winnerValue = new JLabel("-");
    private final JLabel memoValue = new JLabel("-");
    private final JLabel configValue = new JLabel("-");
    private final JLabel replayStepValue = new JLabel("-");
    private final JLabel replayMoveValue = new JLabel("-");
    private final JButton replayBack = new JButton("Anterior");
    private final JButton replayForward = new JButton("Seguent");
    private final JPanel legalMovesPanel = new JPanel(new GridLayout(0, 6, 6, 6));
    private GameViewListener listener;
    private boolean adjusting;
    private long keypadSeed = System.nanoTime();
    private GameInput currentInput;
    private GameResult currentResult;
    private List<ReplayStep> replay = List.of();
    private int replayIndex;

    public GameFrame() {
        super("Joc de la Calculadora");
        configureWindow();
        addLiveUpdates();
    }

    @Override
    public void setListener(GameViewListener listener) {
        this.listener = listener;
    }

    private void configureWindow() {
        getContentPane().setBackground(BACKGROUND);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        add(formPanel(), BorderLayout.NORTH);
        add(centerPanel(), BorderLayout.CENTER);
        pack();
        setMinimumSize(new Dimension(980, 620));
        setLocationRelativeTo(null);
    }

    private JPanel formPanel() {
        JPanel panel = card(new BorderLayout(10, 10), "Configuracio");
        JLabel hint = new JLabel("Canvia qualsevol camp i el resultat es recalcula automaticament.");
        hint.setForeground(MUTED);

        JPanel fields = new JPanel(new GridLayout(3, 3, 8, 8));
        fields.setOpaque(false);
        fields.add(fieldCard("Teclat", keypadModePanel(), PRIMARY));
        fields.add(fieldCard("Algorisme", solverMode, PRIMARY));
        fields.add(fieldCard("Amplada", width, PRIMARY));
        fields.add(fieldCard("Alt", height, PRIMARY));
        fields.add(fieldCard("Nombre inicial", initialTotal, SUCCESS));
        fields.add(fieldCard("Limit", limit, DANGER));
        fields.add(fieldCard("Darrera tecla", lastPlayed, WARNING));
        fields.add(fieldCard("Jugadors", playerCount, PRIMARY));
        fields.add(fieldCard("Torn", startingPlayer, SUCCESS));

        panel.add(hint, BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);
        return panel;
    }

    private JPanel keypadModePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setOpaque(false);
        regenerateKeypad.setFocusPainted(false);
        regenerateKeypad.setEnabled(false);
        regenerateKeypad.addActionListener(event -> {
            keypadSeed = System.nanoTime();
            notifyConfigurationChanged();
        });
        panel.add(keypadMode, BorderLayout.CENTER);
        panel.add(regenerateKeypad, BorderLayout.EAST);
        return panel;
    }

    private JPanel centerPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.add(keypadCard(), BorderLayout.CENTER);
        panel.add(resultsPanel(), BorderLayout.EAST);
        return panel;
    }

    private JPanel keypadCard() {
        JPanel wrapper = card(new BorderLayout(10, 10), "Teclat de la calculadora");
        JLabel hint = new JLabel("Blau: legal. Groc: darrer seleccionat. Gris: no legal en aquest pas.");
        hint.setForeground(MUTED);
        keypadPanel.setOpaque(false);
        wrapper.add(hint, BorderLayout.NORTH);
        wrapper.add(keypadPanel, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel resultsPanel() {
        JPanel panel = card(new GridBagLayout(), "Resultat");
        panel.setPreferredSize(new Dimension(340, 500));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridx = 0;
        c.insets.set(6, 4, 6, 4);

        status.setForeground(MUTED);
        c.gridy = 0;
        panel.add(status, c);
        c.gridy = 1;
        panel.add(metricCard("Perd", loserValue, DANGER), c);
        c.gridy = 2;
        panel.add(metricCard("Guanya", winnerValue, SUCCESS), c);
        c.gridy = 3;
        panel.add(metricCard("Estats calculats", memoValue, PRIMARY), c);
        c.gridy = 4;
        panel.add(replayCard(), c);
        c.gridy = 5;
        panel.add(legalMovesCard(), c);
        c.gridy = 6;
        c.weighty = 1;
        c.anchor = GridBagConstraints.SOUTH;
        panel.add(configCard(), c);
        return panel;
    }

    private JPanel replayCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        JPanel body = new JPanel(new GridLayout(2, 1, 4, 4));
        body.setOpaque(false);
        replayStepValue.setForeground(PRIMARY);
        replayStepValue.setFont(replayStepValue.getFont().deriveFont(Font.BOLD, 16f));
        replayMoveValue.setForeground(INK);
        body.add(replayStepValue);
        body.add(replayMoveValue);

        JPanel navigation = new JPanel(new GridLayout(1, 2, 6, 6));
        navigation.setOpaque(false);
        replayBack.setFocusPainted(false);
        replayForward.setFocusPainted(false);
        replayBack.addActionListener(event -> moveReplay(-1));
        replayForward.addActionListener(event -> moveReplay(1));
        navigation.add(replayBack);
        navigation.add(replayForward);

        panel.add(smallTitle("Reproduccio"), BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        panel.add(navigation, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel legalMovesCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        JLabel title = smallTitle("Moviments legals");
        legalMovesPanel.setOpaque(false);
        panel.add(title, BorderLayout.NORTH);
        panel.add(legalMovesPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel configCard() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setOpaque(false);
        panel.add(smallTitle("Situacio actual"), BorderLayout.NORTH);
        configValue.setForeground(MUTED);
        configValue.setVerticalAlignment(SwingConstants.TOP);
        panel.add(configValue, BorderLayout.CENTER);
        return panel;
    }

    private JPanel metricCard(String title, JLabel value, Color color) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(new Color(249, 250, 251));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        JLabel label = smallTitle(title);
        value.setForeground(color);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 24f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private JPanel fieldCard(String title, java.awt.Component editor, Color color) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(new Color(249, 250, 251));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        JLabel label = smallTitle(title);
        label.setForeground(color);
        panel.add(label, BorderLayout.NORTH);
        panel.add(editor, BorderLayout.CENTER);
        return panel;
    }

    private void addLiveUpdates() {
        for (JSpinner spinner : new JSpinner[]{width, height, initialTotal, limit, lastPlayed, playerCount, startingPlayer}) {
            spinner.addChangeListener(event -> notifyConfigurationChanged());
        }
        keypadMode.addActionListener(event -> {
            boolean random = selectedKeypadMode() == KeypadMode.RANDOM;
            regenerateKeypad.setEnabled(random);
            if (random) keypadSeed = System.nanoTime();
            notifyConfigurationChanged();
        });
        solverMode.addActionListener(event -> notifyConfigurationChanged());
    }

    private void notifyConfigurationChanged() {
        if (!adjusting && listener != null) listener.configurationChanged();
    }

    @Override
    public GameInput input() {
        updateSpinnerBounds();
        return new GameInput(
                value(width),
                value(height),
                value(initialTotal),
                value(limit),
                value(lastPlayed),
                value(playerCount),
                value(startingPlayer),
                selectedKeypadMode(),
                keypadSeed,
                selectedSolverMode()
        );
    }

    private void updateSpinnerBounds() {
        adjusting = true;
        try {
            // Dimension/player changes can make previous spinner values invalid.
            int keyCount = value(width) * value(height);
            clamp(lastPlayed, keyCount);
            clamp(startingPlayer, value(playerCount));
        } finally {
            adjusting = false;
        }
    }

    private void renderKeypad(GameResult result, ReplayStep step) {
        keypadPanel.removeAll();
        keypadPanel.setLayout(new GridLayout(result.keypad().height(), result.keypad().width(), 12, 12));

        int[] legalMoves = step.legalMoves();
        for (int row = 0; row < result.keypad().height(); row++) {
            for (int col = 0; col < result.keypad().width(); col++) {
                int value = result.keypad().value(row, col);
                JLabel key = keypadKey(value, contains(legalMoves, value), value == step.lastPlayed());
                keypadPanel.add(key);
            }
        }
        keypadPanel.revalidate();
        keypadPanel.repaint();
    }

    private JLabel keypadKey(int value, boolean legal, boolean last) {
        JLabel key = new JLabel(String.valueOf(value), JLabel.CENTER);
        key.setOpaque(true);
        key.setPreferredSize(new Dimension(72, 58));
        key.setBackground(last ? KEY_LAST : legal ? KEY_LEGAL : KEY);
        key.setForeground(last ? WARNING : legal ? PRIMARY : MUTED);
        key.setFont(key.getFont().deriveFont(Font.BOLD, 22f));
        key.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(last ? WARNING : legal ? PRIMARY : new Color(209, 213, 219), 2),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        return key;
    }

    @Override
    public void showResult(GameInput input, GameResult result) {
        currentInput = input;
        currentResult = result;
        replay = result.replay();
        replayIndex = 0;
        status.setText("Resolucio actualitzada automaticament");
        status.setForeground(MUTED);
        loserValue.setText("Jugador " + result.losingPlayer());
        winnerValue.setText(result.winningPlayer() == 0 ? "-" : "Jugador " + result.winningPlayer());
        memoValue.setText(String.valueOf(result.computedStates()));
        renderReplayStep();
    }

    private void moveReplay(int delta) {
        if (currentResult == null || replay.isEmpty()) return;
        int next = Math.max(0, Math.min(replay.size() - 1, replayIndex + delta));
        if (next == replayIndex) return;
        replayIndex = next;
        renderReplayStep();
    }

    private void renderReplayStep() {
        if (currentResult == null || replay.isEmpty()) return;
        ReplayStep step = replay.get(replayIndex);
        renderKeypad(currentResult, step);
        renderLegalMoves(step.legalMoves());
        renderCurrentSituation(step);
        replayStepValue.setText("Pas " + step.index() + " de " + (replay.size() - 1));
        replayMoveValue.setText(replayText(step));
        replayBack.setEnabled(replayIndex > 0);
        replayForward.setEnabled(replayIndex < replay.size() - 1);
    }

    private void renderCurrentSituation(ReplayStep step) {
        if (currentInput == null) return;
        String nextTurn = step.terminal() ? "partida acabada" : "Jugador " + step.currentPlayer();
        configValue.setText("<html>pas=" + step.index()
                + "<br>total=" + step.total()
                + "<br>darrera tecla=" + step.lastPlayed()
                + "<br>torn=" + nextTurn
                + "<br>limit=" + currentInput.limit()
                + "<br>jugadors=" + currentInput.playerCount()
                + "<br>teclat=" + (currentInput.keypadMode() == KeypadMode.RANDOM ? "aleatori" : "estandard")
                + "<br>algorisme=" + (currentInput.solverMode() == SolverMode.BOTTOM_UP_DP ? "DP bottom-up" : "DP top-down") + "</html>");
    }

    private String replayText(ReplayStep step) {
        if (step.initial()) return "Inici: total " + step.total() + ". Torn Jugador " + step.currentPlayer() + ".";
        String text = "J" + step.movedPlayer() + " juga " + step.move() + ": "
                + step.totalBefore() + " -> " + step.total() + ".";
        if (step.terminal()) return text + " Perd J" + step.movedPlayer() + ".";
        return text + " Ara J" + step.currentPlayer() + ".";
    }

    private void renderLegalMoves(int[] legalMoves) {
        legalMovesPanel.removeAll();
        if (legalMoves.length == 0) {
            legalMovesPanel.add(chip("cap", MUTED, KEY));
        }
        for (int move : legalMoves) legalMovesPanel.add(chip(String.valueOf(move), PRIMARY, KEY_LEGAL));
        legalMovesPanel.revalidate();
        legalMovesPanel.repaint();
    }

    @Override
    public void showError(String message) {
        status.setText("Revisa la configuracio");
        status.setForeground(DANGER);
        loserValue.setText("-");
        winnerValue.setText("-");
        memoValue.setText("-");
        replayStepValue.setText("-");
        replayMoveValue.setText("-");
        replayBack.setEnabled(false);
        replayForward.setEnabled(false);
        currentResult = null;
        currentInput = null;
        replay = List.of();
        replayIndex = 0;
        configValue.setText("<html><span style='color:#dc2626'>" + message + "</span></html>");
        legalMovesPanel.removeAll();
        legalMovesPanel.add(chip("Error", DANGER, new Color(254, 226, 226)));
        legalMovesPanel.revalidate();
        legalMovesPanel.repaint();
    }

    private static JPanel card(java.awt.LayoutManager layout, String title) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }

    private static JLabel chip(String text, Color foreground, Color background) {
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setOpaque(true);
        label.setForeground(foreground);
        label.setBackground(background);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(foreground),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return label;
    }

    private static JLabel smallTitle(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private static JSpinner spinner(int value, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setPreferredSize(new Dimension(88, 28));
        return spinner;
    }

    private static int value(JSpinner spinner) {
        return (Integer) spinner.getValue();
    }

    private KeypadMode selectedKeypadMode() {
        return keypadMode.getSelectedIndex() == 1 ? KeypadMode.RANDOM : KeypadMode.STANDARD;
    }

    private SolverMode selectedSolverMode() {
        return solverMode.getSelectedIndex() == 1 ? SolverMode.BOTTOM_UP_DP : SolverMode.TOP_DOWN_DP;
    }

    private static void clamp(JSpinner spinner, int max) {
        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
        model.setMaximum(max);
        if (value(spinner) > max) spinner.setValue(max);
    }

    private static boolean contains(int[] values, int target) {
        return Arrays.stream(values).anyMatch(value -> value == target);
    }
}
