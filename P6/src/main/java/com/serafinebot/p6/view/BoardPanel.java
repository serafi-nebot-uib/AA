package com.serafinebot.p6.view;

import com.serafinebot.p6.model.Board;
import com.serafinebot.p6.model.Cell;
import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.Move;
import com.serafinebot.p6.model.MoveType;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class BoardPanel extends JPanel {

    private static final Color BOARD = new Color(37, 99, 235);
    private static final Color EMPTY = new Color(239, 246, 255);
    private static final Color RED = new Color(220, 38, 38);
    private static final Color YELLOW = new Color(250, 204, 21);
    private static final Color GRID = new Color(30, 64, 175);
    private static final Color TEXT = new Color(31, 41, 55);

    private GameState state = new GameState();
    private GameViewListener listener;
    private boolean humanControlsEnabled = true;
    private Timer animationTimer;
    private Cell animatedCell = Cell.EMPTY;
    private int animatedColumn = -1;
    private int animatedTargetRow = -1;
    private double animatedProgress;
    private GameState rotationBefore;
    private Move rotationMove;
    private double rotationProgress;

    public BoardPanel() {
        setPreferredSize(new Dimension(560, 600));
        setBackground(Color.WHITE);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                handleClick(event.getX(), event.getY());
            }
        });
    }

    public void setListener(GameViewListener listener) {
        this.listener = listener;
    }

    public void setState(GameState state) {
        stopAnimation();
        this.state = state;
        repaint();
    }

    public void animateMove(GameState before, GameState after, Move move, Runnable onFinished) {
        stopAnimation();
        if (move.type() == MoveType.ROTATE_LEFT || move.type() == MoveType.ROTATE_RIGHT) {
            animateRotation(before, after, move, onFinished);
            return;
        }

        if (move.type() != MoveType.DROP) {
            state = after;
            repaint();
            onFinished.run();
            return;
        }

        int targetRow = landingRow(before, after, move.column());
        if (targetRow < 0) {
            state = after;
            repaint();
            onFinished.run();
            return;
        }

        state = before;
        animatedColumn = move.column();
        animatedTargetRow = targetRow;
        animatedCell = after.board().cellAt(targetRow, move.column());
        animatedProgress = 0;

        long start = System.nanoTime();
        int durationMs = 260 + targetRow * 35;
        animationTimer = new Timer(16, event -> {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            animatedProgress = Math.min(1.0, elapsedMs / (double) durationMs);
            repaint();
            if (animatedProgress >= 1.0) {
                stopAnimation();
                state = after;
                repaint();
                onFinished.run();
            }
        });
        animationTimer.start();
    }

    public void setHumanControlsEnabled(boolean enabled) {
        this.humanControlsEnabled = enabled;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cell = cellSize();
        int left = leftOffset(cell);
        int top = topOffset(cell);

        drawColumnLabels(g, cell, left, top);
        g.setColor(BOARD);
        g.fillRoundRect(left, top, cell * Board.SIZE, cell * Board.SIZE, 22, 22);

        if (rotationBefore != null) {
            drawRotatingBoard(g, cell, left, top);
            g.dispose();
            return;
        }

        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                int x = left + column * cell;
                int y = top + row * cell;
                drawCell(g, row, column, x, y, cell);
            }
        }

        drawAnimatedPiece(g, cell, left, top);

        g.dispose();
    }

    private void drawColumnLabels(Graphics2D g, int cell, int left, int top) {
        g.setColor(TEXT);
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        FontMetrics metrics = g.getFontMetrics();
        for (int column = 0; column < Board.SIZE; column++) {
            String label = Integer.toString(column + 1);
            int x = left + column * cell + (cell - metrics.stringWidth(label)) / 2;
            g.drawString(label, x, top - 12);
        }
    }

    private void drawCell(Graphics2D g, int row, int column, int x, int y, int cell) {
        int margin = Math.max(7, cell / 10);
        int size = cell - margin * 2;

        g.setColor(colorFor(state.board().cellAt(row, column)));
        g.fillOval(x + margin, y + margin, size, size);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x + margin, y + margin, size, size);

        if (state.board().canRemove(row, column, state.currentPlayer())) {
            g.setColor(new Color(17, 24, 39, 150));
            g.drawLine(x + margin + 8, y + margin + 8, x + margin + size - 8, y + margin + size - 8);
            g.drawLine(x + margin + size - 8, y + margin + 8, x + margin + 8, y + margin + size - 8);
        }
    }

    private Color colorFor(Cell cell) {
        return switch (cell) {
            case EMPTY -> EMPTY;
            case RED -> RED;
            case YELLOW -> YELLOW;
        };
    }

    private void drawAnimatedPiece(Graphics2D g, int cell, int left, int top) {
        if (animatedColumn < 0 || animatedTargetRow < 0 || animatedCell == Cell.EMPTY) {
            return;
        }

        int margin = Math.max(7, cell / 10);
        int size = cell - margin * 2;
        double eased = 1 - Math.pow(1 - animatedProgress, 3);
        int x = left + animatedColumn * cell + margin;
        int startY = top - cell + margin;
        int endY = top + animatedTargetRow * cell + margin;
        int y = startY + (int) Math.round((endY - startY) * eased);

        g.setColor(colorFor(animatedCell));
        g.fillOval(x, y, size, size);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x, y, size, size);
    }

    private void drawRotatingBoard(Graphics2D g, int cell, int left, int top) {
        Shape oldClip = g.getClip();
        g.setClip(left, top, cell * Board.SIZE, cell * Board.SIZE);

        AffineTransform oldTransform = g.getTransform();
        double eased = 1 - Math.pow(1 - rotationProgress, 3);
        double angle = (rotationMove.type() == MoveType.ROTATE_LEFT ? -Math.PI / 2 : Math.PI / 2) * eased;
        double centerX = left + cell * Board.SIZE / 2.0;
        double centerY = top + cell * Board.SIZE / 2.0;
        g.rotate(angle, centerX, centerY);

        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                int x = left + column * cell;
                int y = top + row * cell;
                drawCellFrom(g, rotationBefore, row, column, x, y, cell, false);
            }
        }

        g.setTransform(oldTransform);
        g.setClip(oldClip);
    }

    private void handleClick(int x, int y) {
        if (!humanControlsEnabled || listener == null || animationTimer != null) {
            return;
        }

        int cell = cellSize();
        int left = leftOffset(cell);
        int top = topOffset(cell);
        if (x < left || y < top || x >= left + cell * Board.SIZE || y >= top + cell * Board.SIZE) {
            return;
        }

        int column = (x - left) / cell;
        int row = (y - top) / cell;
        if (state.board().canRemove(row, column, state.currentPlayer())) {
            listener.onRemove(row, column);
        } else {
            listener.onDrop(column);
        }
    }

    private int cellSize() {
        return Math.min((getWidth() - 70) / Board.SIZE, (getHeight() - 90) / Board.SIZE);
    }

    private int leftOffset(int cell) {
        return (getWidth() - cell * Board.SIZE) / 2;
    }

    private int topOffset(int cell) {
        return Math.max(44, (getHeight() - cell * Board.SIZE) / 2 + 20);
    }

    private void drawCellFrom(Graphics2D g, GameState source, int row, int column, int x, int y, int cell, boolean allowRemoveMark) {
        int margin = Math.max(7, cell / 10);
        int size = cell - margin * 2;

        g.setColor(colorFor(source.board().cellAt(row, column)));
        g.fillOval(x + margin, y + margin, size, size);
        g.setColor(GRID);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x + margin, y + margin, size, size);

        if (allowRemoveMark && source.board().canRemove(row, column, source.currentPlayer())) {
            g.setColor(new Color(17, 24, 39, 150));
            g.drawLine(x + margin + 8, y + margin + 8, x + margin + size - 8, y + margin + size - 8);
            g.drawLine(x + margin + size - 8, y + margin + 8, x + margin + 8, y + margin + size - 8);
        }
    }

    private int landingRow(GameState before, GameState after, int column) {
        for (int row = 0; row < Board.SIZE; row++) {
            if (before.board().cellAt(row, column) != after.board().cellAt(row, column)) {
                return row;
            }
        }
        return -1;
    }

    private void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        animatedCell = Cell.EMPTY;
        animatedColumn = -1;
        animatedTargetRow = -1;
        animatedProgress = 0;
        rotationBefore = null;
        rotationMove = null;
        rotationProgress = 0;
    }

    private void animateRotation(GameState before, GameState after, Move move, Runnable onFinished) {
        state = after;
        rotationBefore = before;
        rotationMove = move;
        rotationProgress = 0;

        long start = System.nanoTime();
        int durationMs = 420;
        animationTimer = new Timer(16, event -> {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            rotationProgress = Math.min(1.0, elapsedMs / (double) durationMs);
            repaint();
            if (rotationProgress >= 1.0) {
                stopAnimation();
                state = after;
                repaint();
                onFinished.run();
            }
        });
        animationTimer.start();
    }
}
