package com.serafinebot.p4.view;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Simple bar chart used for categorical benchmark comparisons.
 */
public final class BenchmarkBarChartPanel extends JPanel {

    public record Bar(String label, Color color, double value) {
    }

    private String title = "";
    private String yLabel = "";
    private String emptyMessage = "No hi ha dades.";
    private List<Bar> bars = List.of();

    public BenchmarkBarChartPanel() {
        setOpaque(true);
        setBackground(Color.WHITE);
    }

    public void setChart(String title, String yLabel, String emptyMessage, List<Bar> bars) {
        this.title = title;
        this.yLabel = yLabel;
        this.emptyMessage = emptyMessage;
        this.bars = List.copyOf(bars);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawTitle(g2);
        if (bars.isEmpty()) {
            drawEmptyMessage(g2);
            g2.dispose();
            return;
        }

        int left = 88;
        int top = 46;
        int width = Math.max(120, getWidth() - left - 24);
        int height = Math.max(120, getHeight() - 92);
        double minValue = Math.min(0.0, bars.stream().mapToDouble(Bar::value).min().orElse(0.0));
        double maxValue = Math.max(0.0, bars.stream().mapToDouble(Bar::value).max().orElse(1.0));
        if (Double.compare(minValue, maxValue) == 0) {
            maxValue = minValue + 1.0;
        }
        ChartAxisSupport.AxisValueFormatter yFormatter = ChartAxisSupport.formatter(yLabel, minValue, maxValue);
        ChartAxisSupport.AxisTicks yTicks = yFormatter.ticks(
            minValue,
            maxValue,
            ChartAxisSupport.suggestTickCount(height)
        );
        String renderedYLabel = yFormatter.axisLabel(yLabel);
        double axisMin = yTicks.min();
        double axisMax = yTicks.max();
        int baselineY = valueToY(0.0, top, height, axisMin, axisMax);

        g2.setColor(new Color(82, 90, 105));
        g2.drawLine(left, baselineY, left + width, baselineY);
        g2.drawLine(left, top, left, top + height);

        Font labelFont = getFont().deriveFont(Font.PLAIN, 11f);
        g2.setFont(labelFont);
        FontMetrics metrics = g2.getFontMetrics();

        for (double tick : yTicks.values()) {
            int y = valueToY(tick, top, height, axisMin, axisMax);
            g2.setColor(new Color(232, 236, 242));
            g2.drawLine(left, y, left + width, y);
            g2.setColor(new Color(82, 90, 105));
            String valueLabel = yFormatter.format(tick);
            g2.drawString(valueLabel, left - metrics.stringWidth(valueLabel) - 8, y + metrics.getAscent() / 2);
        }

        int slotWidth = width / Math.max(1, bars.size());
        int barWidth = Math.max(22, slotWidth - 26);
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            int valueY = valueToY(bar.value(), top, height, axisMin, axisMax);
            int y = Math.min(valueY, baselineY);
            int barHeight = Math.max(1, Math.abs(valueY - baselineY));
            int x = left + i * slotWidth + (slotWidth - barWidth) / 2;

            g2.setColor(bar.color());
            g2.fillRoundRect(x, y, barWidth, barHeight, 12, 12);
            g2.setColor(bar.color().darker());
            g2.drawRoundRect(x, y, barWidth, barHeight, 12, 12);

            g2.setColor(new Color(47, 57, 74));
            String label = bar.label();
            g2.drawString(label, x + (barWidth - metrics.stringWidth(label)) / 2, top + height + 18);

            String valueLabel = yFormatter.format(bar.value());
            int valueLabelY = bar.value() >= 0.0
                ? y - 6
                : y + barHeight + metrics.getAscent() + 2;
            valueLabelY = Math.max(top + metrics.getAscent(), Math.min(top + height - 2, valueLabelY));
            g2.drawString(valueLabel, x + (barWidth - metrics.stringWidth(valueLabel)) / 2, valueLabelY);
        }

        drawYAxisLabel(g2, renderedYLabel, top, height);
        g2.dispose();
    }

    private void drawYAxisLabel(Graphics2D g2, String axisLabel, int top, int height) {
        Font font = getFont().deriveFont(Font.PLAIN, 12f);
        Graphics2D rotated = (Graphics2D) g2.create();
        rotated.setFont(font);
        rotated.setColor(new Color(47, 57, 74));
        FontMetrics metrics = rotated.getFontMetrics();
        rotated.rotate(-Math.PI / 2);
        rotated.drawString(axisLabel, -(top + height / 2 + metrics.stringWidth(axisLabel) / 2), 18);
        rotated.dispose();
    }

    private int valueToY(double value, int top, int height, double axisMin, double axisMax) {
        double ratio = (value - axisMin) / (axisMax - axisMin);
        return top + height - (int) Math.round(ratio * height);
    }

    private void drawTitle(Graphics2D g2) {
        g2.setColor(new Color(47, 57, 74));
        Font font = getFont().deriveFont(Font.BOLD, 15f);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        int x = Math.max(12, (getWidth() - metrics.stringWidth(title)) / 2);
        g2.drawString(title, x, 24);
    }

    private void drawEmptyMessage(Graphics2D g2) {
        g2.setColor(new Color(107, 118, 136));
        Font font = getFont().deriveFont(Font.PLAIN, 14f);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        int x = Math.max(20, (getWidth() - metrics.stringWidth(emptyMessage)) / 2);
        int y = getHeight() / 2;
        g2.drawString(emptyMessage, x, y);
    }
}
