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
 * <p>The component receives already-computed turn counts and performs only visual
 * bucketing. It has no knowledge of game rules or simulation execution, which
 * keeps it safely inside the view layer.</p>
 */
final class HistogramPanel extends javax.swing.JPanel {

    private static final int MAX_BUCKETS = 24;
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color AXIS = new Color(75, 85, 99);
    private static final Color BAR = new Color(37, 99, 235);
    private static final Color BAR_TOP = new Color(96, 165, 250);
    private static final Color MUTED = new Color(107, 114, 128);

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

        int left = 54;
        int right = 18;
        int top = 26;
        int bottom = 44;
        int chartWidth = width - left - right;
        int chartHeight = height - top - bottom;
        if (chartWidth <= 0 || chartHeight <= 0) {
            g.dispose();
            return;
        }

        int min = Arrays.stream(observations).min().orElse(0);
        int max = Arrays.stream(observations).max().orElse(min);
        int[] frequencies = frequencies(min, max);
        int maxFrequency = Arrays.stream(frequencies).max().orElse(1);

        g.setColor(AXIS);
        g.drawLine(left, top, left, top + chartHeight);
        g.drawLine(left, top + chartHeight, left + chartWidth, top + chartHeight);

        int slot = Math.max(1, chartWidth / frequencies.length);
        int gap = Math.min(5, Math.max(1, slot / 5));
        int barWidth = Math.max(1, slot - gap);
        for (int i = 0; i < frequencies.length; i++) {
            // Bars are scaled against the most populated bucket, not against the
            // number of simulations. This keeps sparse and dense runs readable.
            int barHeight = (int) Math.round(frequencies[i] * (chartHeight - 6) / (double) maxFrequency);
            int x = left + i * slot + gap / 2;
            int y = top + chartHeight - barHeight;
            g.setColor(BAR);
            g.fillRoundRect(x, y, barWidth, barHeight, 4, 4);
            g.setColor(BAR_TOP);
            g.drawRoundRect(x, y, barWidth, barHeight, 4, 4);
        }

        g.setColor(MUTED);
        g.drawString("Freq.", 8, top + chartHeight / 2);
        g.drawString(Integer.toString(maxFrequency), 8, top + 4);
        g.drawString(Integer.toString(min), left, top + chartHeight + 18);
        String maxLabel = Integer.toString(max);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(maxLabel, left + chartWidth - metrics.stringWidth(maxLabel), top + chartHeight + 18);
        String axisLabel = "Torns observats";
        g.drawString(axisLabel, left + (chartWidth - metrics.stringWidth(axisLabel)) / 2, height - 10);

        g.dispose();
    }

    private int[] frequencies(int min, int max) {
        int bucketCount = Math.min(MAX_BUCKETS, Math.max(1, max - min + 1));
        int span = max - min + 1;
        int[] frequencies = new int[bucketCount];
        for (int observation : observations) {
            int bucket = (int) ((long) (observation - min) * bucketCount / span);
            frequencies[Math.min(bucket, bucketCount - 1)]++;
        }
        return frequencies;
    }

    private static void drawCentered(Graphics2D g, String text, int width, int height) {
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(MUTED);
        g.drawString(text, (width - metrics.stringWidth(text)) / 2, height / 2);
    }
}
