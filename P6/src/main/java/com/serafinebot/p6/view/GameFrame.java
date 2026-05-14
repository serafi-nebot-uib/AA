package com.serafinebot.p6.view;

import com.serafinebot.p6.model.GameResult;
import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.GameStatus;
import com.serafinebot.p6.model.Move;
import com.serafinebot.p6.model.Player;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;

/**
 * Main Swing window for P6.
 *
 * <p>The frame assembles the board, mode selectors, player badges, status panel
 * and playback controls. It implements {@link GameView}, so all state changes
 * still flow through the controller instead of being handled directly here.</p>
 */
public final class GameFrame extends JFrame implements GameView {

    private static final Color BACKGROUND = new Color(244, 246, 250);
    private static final Color CARD = Color.WHITE;
    private static final Color INK = new Color(31, 41, 55);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color PRIMARY = new Color(96, 165, 250);
    private static final Color PRIMARY_DARK = new Color(37, 99, 235);
    private static final Color RED = new Color(220, 38, 38);
    private static final Color YELLOW = new Color(250, 204, 21);
    private static final Color STATUS_BG = new Color(239, 246, 255);

    private final BoardPanel boardPanel = new BoardPanel();
    private final JComboBox<MatchType> matchType = new JComboBox<>(MatchType.values());
    private final JComboBox<AgentType> redAgent = new JComboBox<>(AgentType.values());
    private final JComboBox<AgentType> yellowAgent = new JComboBox<>(AgentType.values());
    private final JButton newGame = button("Nova partida", PRIMARY);
    private final JButton rotateLeft = rotateButton("↶", "Rotar esquerra");
    private final JButton rotateRight = rotateButton("↷", "Rotar dreta");
    private final JButton play = button("▶ Reproduir", new Color(5, 150, 105));
    private final JButton stop = button("■ Aturar", new Color(220, 38, 38));
    private final JButton next = button("Següent", PRIMARY);
    private final JButton previous = button("Anterior", new Color(107, 114, 128));
    private final JLabel turn = new JLabel("Torn: Vermell");
    private final JLabel status = new JLabel(" ");
    private final JLabel stats = new JLabel(" ");
    private final JPanel turnDot = new JPanel();
    private GameViewListener listener;
    private GameStatus lastNotifiedStatus = GameStatus.IN_PROGRESS;

    public GameFrame() {
        super("Connecta 4 Rotatori - P6");
        configureWindow();
        wireActions();
    }

    @Override
    public void setListener(GameViewListener listener) {
        this.listener = listener;
        boardPanel.setListener(listener);
    }

    @Override
    public MatchType selectedMatchType() {
        return (MatchType) matchType.getSelectedItem();
    }

    @Override
    public AgentType selectedRedAgent() {
        return (AgentType) redAgent.getSelectedItem();
    }

    @Override
    public AgentType selectedYellowAgent() {
        return (AgentType) yellowAgent.getSelectedItem();
    }

    @Override
    public void showState(GameState state) {
        boardPanel.setState(state);
        turn.setText("Torn: " + playerName(state.currentPlayer()));
        turnDot.setBackground(playerColor(state.currentPlayer()));
        stats.setText("Fitxes: " + state.board().pieceCount() + " / 49");
        showResult(state.result());
        notifyResultIfNeeded(state.result());
    }

    @Override
    public void animateMove(GameState before, GameState after, Move move, Runnable onFinished) {
        boardPanel.animateMove(before, after, move, onFinished);
    }

    @Override
    public void showMessage(String message) {
        status.setText(message);
    }

    @Override
    public void setHumanControlsEnabled(boolean enabled) {
        boardPanel.setHumanControlsEnabled(enabled);
        rotateLeft.setEnabled(enabled);
        rotateRight.setEnabled(enabled);
    }

    @Override
    public void setPlaybackControls(boolean playEnabled, boolean stopEnabled, boolean nextEnabled, boolean previousEnabled) {
        play.setEnabled(playEnabled);
        stop.setEnabled(stopEnabled);
        next.setEnabled(nextEnabled);
        previous.setEnabled(previousEnabled);
    }

    private void configureWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BACKGROUND);
        setLayout(new BorderLayout(14, 14));
        add(header(), BorderLayout.NORTH);
        add(boardArea(), BorderLayout.CENTER);
        add(sidebar(), BorderLayout.EAST);
        pack();
        setMinimumSize(new Dimension(900, 660));
        setLocationRelativeTo(null);
    }

    private JPanel header() {
        JPanel panel = card(new BorderLayout(8, 4));
        JLabel title = new JLabel("Variant de Connecta 4");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Inserir, eliminar fitxes propies o rotar el tauler 90 graus.");
        subtitle.setForeground(MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.CENTER);
        return panel;
    }

    private JPanel boardArea() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.add(rotateRail(rotateLeft, "Esquerra"), BorderLayout.WEST);
        panel.add(boardPanel, BorderLayout.CENTER);
        panel.add(rotateRail(rotateRight, "Dreta"), BorderLayout.EAST);
        return panel;
    }

    private JPanel rotateRail(JButton button, String labelText) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(96, 0));
        JLabel label = new JLabel(labelText, JLabel.CENTER);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(button, BorderLayout.CENTER);
        return panel;
    }

    private JPanel sidebar() {
        JPanel panel = card(new BorderLayout(10, 10));
        panel.setPreferredSize(new Dimension(260, 0));

        // BoxLayout keeps the configuration compact. A GridLayout would assign
        // the same height to every component and spread the controls too far
        // apart vertically.
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setOpaque(false);
        addCompact(controls, label("Tipus de partida"), 3);
        addCompact(controls, matchType, 8);
        addCompact(controls, label("Agent Vermell"), 3);
        addCompact(controls, redAgent, 8);
        addCompact(controls, label("Agent Groc"), 3);
        addCompact(controls, yellowAgent, 10);
        addCompact(controls, newGame, 8);
        addCompact(controls, playbackPanel(), 10);
        addCompact(controls, playersPanel(), 10);
        addCompact(controls, statusPanel(), 0);

        panel.add(controls, BorderLayout.NORTH);
        return panel;
    }

    private void addCompact(JPanel parent, Component component, int bottomGap) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        parent.add(component);
        if (bottomGap > 0) {
            parent.add(Box.createVerticalStrut(bottomGap));
        }
    }

    private JPanel playbackPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
        panel.setOpaque(false);
        play.setToolTipText("Reprodueix automaticament una partida agent contra agent.");
        stop.setToolTipText("Atura la reproduccio automatica.");
        next.setToolTipText("Avanca una jugada amb animacio.");
        previous.setToolTipText("Torna a l'estat anterior.");
        panel.add(play);
        panel.add(stop);
        panel.add(previous);
        panel.add(next);
        return panel;
    }

    private JPanel playersPanel() {
        JPanel panel = card(new GridLayout(0, 1, 8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        panel.add(label("Jugadors"));
        panel.add(playerBadge(Player.RED));
        panel.add(playerBadge(Player.YELLOW));
        return panel;
    }

    private JPanel playerBadge(Player player) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        JPanel dot = new JPanel();
        dot.setPreferredSize(new Dimension(18, 18));
        dot.setBackground(playerColor(player));
        dot.setBorder(BorderFactory.createLineBorder(new Color(17, 24, 39, 80), 1));
        JLabel name = new JLabel(playerName(player));
        name.setForeground(INK);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        panel.add(dot, BorderLayout.WEST);
        panel.add(name, BorderLayout.CENTER);
        return panel;
    }

    private JPanel statusPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(STATUS_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JPanel turnLine = new JPanel(new BorderLayout(8, 0));
        turnLine.setOpaque(false);
        turnDot.setPreferredSize(new Dimension(18, 18));
        turnDot.setBackground(RED);
        turnDot.setBorder(BorderFactory.createLineBorder(new Color(17, 24, 39, 80), 1));
        turn.setForeground(INK);
        turn.setFont(turn.getFont().deriveFont(Font.BOLD, 15f));
        turnLine.add(turnDot, BorderLayout.WEST);
        turnLine.add(turn, BorderLayout.CENTER);

        status.setForeground(PRIMARY_DARK);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 12f));
        stats.setForeground(MUTED);

        panel.add(label("Estat"), BorderLayout.NORTH);
        panel.add(turnLine, BorderLayout.CENTER);
        JPanel details = new JPanel(new GridLayout(0, 1, 3, 3));
        details.setOpaque(false);
        details.add(stats);
        details.add(status);
        panel.add(details, BorderLayout.SOUTH);
        return panel;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setForeground(INK);
        return label;
    }

    private JPanel card(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));
        return panel;
    }

    private JButton button(String text, Color color) {
        JButton button = new SolidButton(text, color);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        button.setRolloverEnabled(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        return button;
    }

    private JButton rotateButton(String arrow, String tooltip) {
        JButton button = button("<html><div style='text-align:center;font-size:34px;'>" + arrow
                + "</div><div style='text-align:center;font-size:11px;'>" + tooltip + "</div></html>", PRIMARY);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(88, 180));
        return button;
    }

    private void wireActions() {
        redAgent.setSelectedItem(AgentType.GREEDY);
        yellowAgent.setSelectedItem(AgentType.MINIMAX);
        updateAgentSelectors();
        newGame.addActionListener(event -> {
            if (listener != null) {
                listener.onNewGame(selectedMatchType(), selectedRedAgent(), selectedYellowAgent());
            }
        });
        matchType.addActionListener(event -> {
            updateAgentSelectors();
            if (listener != null) {
                listener.onNewGame(selectedMatchType(), selectedRedAgent(), selectedYellowAgent());
            }
        });
        redAgent.addActionListener(event -> restartFromSelectors());
        yellowAgent.addActionListener(event -> restartFromSelectors());
        rotateLeft.addActionListener(event -> {
            if (listener != null) {
                listener.onRotateLeft();
            }
        });
        rotateRight.addActionListener(event -> {
            if (listener != null) {
                listener.onRotateRight();
            }
        });
        play.addActionListener(event -> {
            if (listener != null) {
                listener.onPlay();
            }
        });
        stop.addActionListener(event -> {
            if (listener != null) {
                listener.onStop();
            }
        });
        next.addActionListener(event -> {
            if (listener != null) {
                listener.onNext();
            }
        });
        previous.addActionListener(event -> {
            if (listener != null) {
                listener.onPrevious();
            }
        });
    }

    private void restartFromSelectors() {
        if (listener != null) {
            listener.onNewGame(selectedMatchType(), selectedRedAgent(), selectedYellowAgent());
        }
    }

    private void updateAgentSelectors() {
        // Human-vs-human needs no agents. Human-vs-agent fixes Red as the human
        // player and lets the user choose Yellow. Agent-vs-agent exposes both.
        boolean agentVsAgent = selectedMatchType() == MatchType.AGENT_VS_AGENT;
        boolean humanVsAgent = selectedMatchType() == MatchType.HUMAN_VS_AGENT;
        redAgent.setEnabled(agentVsAgent);
        yellowAgent.setEnabled(agentVsAgent || humanVsAgent);
    }

    private void showResult(GameResult result) {
        if (result.status() == GameStatus.IN_PROGRESS) {
            lastNotifiedStatus = GameStatus.IN_PROGRESS;
            return;
        }
        if (result.status() == GameStatus.DRAW) {
            status.setText("Partida finalitzada: empat");
        } else {
            status.setText("Partida finalitzada: guanya " + playerName(result.winner()));
        }
    }

    private void notifyResultIfNeeded(GameResult result) {
        if (result.status() == GameStatus.IN_PROGRESS || result.status() == lastNotifiedStatus) {
            return;
        }
        lastNotifiedStatus = result.status();

        String message = result.status() == GameStatus.DRAW
                ? "La partida ha acabat en empat."
                : "Guanya " + playerName(result.winner()) + ".";
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this,
                message,
                "Final de partida",
                JOptionPane.INFORMATION_MESSAGE
        ));
    }

    private String playerName(Player player) {
        return player == Player.RED ? "Vermell" : "Groc";
    }

    private Color playerColor(Player player) {
        return player == Player.RED ? RED : YELLOW;
    }

    private static final class SolidButton extends JButton {

        private final Color color;

        private SolidButton(String text, Color color) {
            super(text);
            this.color = color;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            // Some platform Look & Feels, especially macOS, ignore JButton
            // background colors. Painting the rounded background manually keeps
            // enabled/disabled buttons visually consistent.
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isEnabled()) {
                setForeground(Color.WHITE);
                g.setColor(color);
            } else {
                setForeground(new Color(229, 231, 235));
                g.setColor(new Color(156, 163, 175));
            }
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
