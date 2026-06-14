package com.serafinebot.p7.view;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.Objects;

/**
 * Lightweight histogram component for the empirical turn distribution.
 *
 * <p>The component receives already-computed turn counts and maps each integer
 * turn value to its own bar. It has no knowledge of game rules or simulation
 * execution, which keeps it safely inside the view layer.</p>
 */
final class HistogramPanel extends javax.swing.JPanel {

    private static final int TARGET_X_TICKS = 7;
    private static final int TARGET_Y_INTERVALS = 5;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color AXIS = new Color(75, 85, 99);
    private static final Color BAR = new Color(37, 99, 235);
    private static final Color BAR_TOP = new Color(96, 165, 250);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color GRID = new Color(229, 231, 235);

    private int[] observations = new int[0];

    HistogramPanel() {
        setBackground(BACKGROUND);
    }

    /**
     * Replaces the displayed observations and repaints the chart.
     */
    void setObservations(int[] observations) {
        this.observations = Objects.requireNonNull(observations).clone();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        if (observations.length == 0) {
            drawCentered(g, "Executa una simulacio per veure l'histograma.", width, height);
            g.dispose();
            return;
        }

        int left = 62;
        int right = 24;
        int top = 26;
        int bottom = 48;
        int chartWidth = width - left - right;
        int chartHeight = height - top - bottom;
        if (chartWidth <= 0 || chartHeight <= 0) {
            g.dispose();
            return;
        }

        int max = Math.max(1, Arrays.stream(observations).max().orElse(1));
        int[] frequencies = frequencies(max);
        int maxFrequency = Arrays.stream(frequencies).max().orElse(1);
        int yStep = niceStep(maxFrequency, TARGET_Y_INTERVALS);
        int yMax = Math.max(yStep, roundUp(maxFrequency, yStep));

        FontMetrics metrics = g.getFontMetrics();

        drawYAxisTicks(g, left, top, chartHeight, chartWidth, yMax, yStep, metrics);
        drawXAxisTicks(g, left, top + chartHeight, chartWidth, max, metrics);

        g.setColor(AXIS);
        g.drawLine(left, top, left, top + chartHeight);
        g.drawLine(left, top + chartHeight, left + chartWidth, top + chartHeight);

        double slot = chartWidth / (double) frequencies.length;
        for (int i = 0; i < frequencies.length; i++) {
            int barHeight = (int) Math.round(frequencies[i] * (chartHeight - 6) / (double) yMax);
            int x = (int) Math.round(left + i * slot);
            int nextX = (int) Math.round(left + (i + 1) * slot);
            int barWidth = Math.max(1, nextX - x - 1);
            int y = top + chartHeight - barHeight;
            g.setColor(BAR);
            g.fillRect(x, y, barWidth, barHeight);
            g.setColor(BAR_TOP);
            g.drawRect(x, y, barWidth, barHeight);
        }

        g.setColor(MUTED);
        g.drawString("Freq.", 8, top + chartHeight / 2);
        String axisLabel = "Torns observats";
        g.drawString(axisLabel, left + (chartWidth - metrics.stringWidth(axisLabel)) / 2, height - 10);

        g.dispose();
    }

    private static void drawYAxisTicks(Graphics2D g, int left, int top, int chartHeight,
                                       int chartWidth, int yMax, int step, FontMetrics metrics) {
        for (int value = 0; value <= yMax; value += step) {
            int y = top + chartHeight - (int) Math.round(value * chartHeight / (double) yMax);
            String label = Integer.toString(value);
            g.setColor(GRID);
            g.drawLine(left, y, left + chartWidth, y);
            g.setColor(AXIS);
            g.drawLine(left - 4, y, left, y);
            g.setColor(MUTED);
            g.drawString(label, left - 8 - metrics.stringWidth(label), y + metrics.getAscent() / 2 - 2);
        }
    }

    private static void drawXAxisTicks(Graphics2D g, int left, int baseline, int chartWidth,
                                       int max, FontMetrics metrics) {
        int step = niceStep(max, TARGET_X_TICKS);
        for (int value = step; value <= max; value += step) {
            int x = left + (int) Math.round((value - 0.5) * chartWidth / max);
            String label = Integer.toString(value);
            g.setColor(AXIS);
            g.drawLine(x, baseline, x, baseline + 4);
            g.setColor(MUTED);
            g.drawString(label, x - metrics.stringWidth(label) / 2, baseline + metrics.getAscent() + 6);
        }
    }

    private int[] frequencies(int max) {
        int[] frequencies = new int[max];
        for (int observation : observations) {
            if (observation >= 1 && observation <= max) {
                frequencies[observation - 1]++;
            }
        }
        return frequencies;
    }

    private static int niceStep(int maxValue, int targetIntervals) {
        if (maxValue <= 0) {
            return 1;
        }
        double roughStep = maxValue / (double) Math.max(1, targetIntervals);
        double magnitude = Math.pow(10, Math.floor(Math.log10(roughStep)));
        double normalized = roughStep / magnitude;
        double niceNormalized;
        if (normalized <= 1) {
            niceNormalized = 1;
        } else if (normalized <= 2) {
            niceNormalized = 2;
        } else if (normalized <= 5) {
            niceNormalized = 5;
        } else {
            niceNormalized = 10;
        }
        return Math.max(1, (int) Math.round(niceNormalized * magnitude));
    }

    private static int roundUp(int value, int step) {
        return ((value + step - 1) / step) * step;
    }

    private static void drawCentered(Graphics2D g, String text, int width, int height) {
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(MUTED);
        g.drawString(text, (width - metrics.stringWidth(text)) / 2, height / 2);
    }
}
