package com.serafinebot.p4.view;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * Lightweight 2D graph panel for benchmark series.
 */
public final class BenchmarkGraphPanel extends JPanel {

    public record GraphPoint(double x, double y, String label) {
    }

    public record Series(String name, Color color, boolean connectPoints, List<GraphPoint> points) {
        public Series {
            points = List.copyOf(points);
        }
    }

    private static final int LEFT_MARGIN = 88;
    private static final int RIGHT_MARGIN = 24;
    private static final int TOP_MARGIN = 44;
    private static final int BOTTOM_MARGIN = 54;
    private static final Stroke SERIES_STROKE = new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private String title = "";
    private String xLabel = "";
    private String yLabel = "";
    private String emptyMessage = "No hi ha dades.";
    private List<Series> series = List.of();

    public BenchmarkGraphPanel() {
        setOpaque(true);
        setBackground(Color.WHITE);
    }

    public void setGraph(String title, String xLabel, String yLabel, String emptyMessage, List<Series> series) {
        this.title = title;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        this.emptyMessage = emptyMessage;
        this.series = List.copyOf(series);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawTitle(g2);
        if (series.isEmpty() || series.stream().allMatch(serie -> serie.points().isEmpty())) {
            drawEmptyMessage(g2);
            g2.dispose();
            return;
        }

        Bounds dataBounds = computeBounds();
        int width = getWidth();
        int height = getHeight();
        int graphLeft = LEFT_MARGIN;
        int graphTop = TOP_MARGIN;
        int graphWidth = Math.max(120, width - LEFT_MARGIN - RIGHT_MARGIN);
        int graphHeight = Math.max(120, height - TOP_MARGIN - BOTTOM_MARGIN);
        ChartAxisSupport.AxisValueFormatter xFormatter = ChartAxisSupport.formatter(xLabel, dataBounds.minX, dataBounds.maxX);
        ChartAxisSupport.AxisValueFormatter yFormatter = ChartAxisSupport.formatter(yLabel, dataBounds.minY, dataBounds.maxY);
        ChartAxisSupport.AxisTicks xTicks = xFormatter.ticks(
            dataBounds.minX,
            dataBounds.maxX,
            ChartAxisSupport.suggestTickCount(graphWidth)
        );
        ChartAxisSupport.AxisTicks yTicks = yFormatter.ticks(
            dataBounds.minY,
            dataBounds.maxY,
            ChartAxisSupport.suggestTickCount(graphHeight)
        );
        Bounds bounds = new Bounds(xTicks.min(), xTicks.max(), yTicks.min(), yTicks.max());

        drawGrid(g2, graphLeft, graphTop, graphWidth, graphHeight, bounds, xTicks, yTicks);
        drawAxes(g2, graphLeft, graphTop, graphWidth, graphHeight, bounds, xTicks, yTicks, xFormatter, yFormatter);
        drawSeries(g2, graphLeft, graphTop, graphWidth, graphHeight, bounds);
        drawPointLabels(g2, graphLeft, graphTop, graphWidth, graphHeight, bounds);
        drawLegend(g2, graphLeft, graphTop, graphWidth);
        drawLabels(
            g2,
            graphLeft,
            graphTop,
            graphWidth,
            graphHeight,
            xFormatter.axisLabel(xLabel),
            yFormatter.axisLabel(yLabel)
        );

        g2.dispose();
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

    private Bounds computeBounds() {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Series serie : series) {
            for (GraphPoint point : serie.points()) {
                minX = Math.min(minX, point.x());
                maxX = Math.max(maxX, point.x());
                minY = Math.min(minY, point.y());
                maxY = Math.max(maxY, point.y());
            }
        }

        if (Double.compare(minX, maxX) == 0) {
            minX -= 1.0;
            maxX += 1.0;
        }
        if (Double.compare(minY, maxY) == 0) {
            minY -= 1.0;
            maxY += 1.0;
        }

        double yPadding = Math.max(0.1, (maxY - minY) * 0.12);
        double paddedMinY = minY - yPadding;
        double paddedMaxY = maxY + yPadding;
        if (minY >= 0.0) {
            paddedMinY = 0.0;
        }
        if (maxY <= 0.0) {
            paddedMaxY = 0.0;
        }
        return new Bounds(minX, maxX, paddedMinY, paddedMaxY);
    }

    private void drawGrid(
        Graphics2D g2,
        int left,
        int top,
        int width,
        int height,
        Bounds bounds,
        ChartAxisSupport.AxisTicks xTicks,
        ChartAxisSupport.AxisTicks yTicks
    ) {
        g2.setColor(new Color(232, 236, 242));
        for (double xTick : xTicks.values()) {
            int x = valueToX(xTick, left, width, bounds);
            g2.draw(new Line2D.Double(x, top, x, top + height));
        }
        for (double yTick : yTicks.values()) {
            int y = valueToY(yTick, top, height, bounds);
            g2.draw(new Line2D.Double(left, y, left + width, y));
        }
    }

    private void drawAxes(
        Graphics2D g2,
        int left,
        int top,
        int width,
        int height,
        Bounds bounds,
        ChartAxisSupport.AxisTicks xTicks,
        ChartAxisSupport.AxisTicks yTicks,
        ChartAxisSupport.AxisValueFormatter xFormatter,
        ChartAxisSupport.AxisValueFormatter yFormatter
    ) {
        g2.setColor(new Color(82, 90, 105));
        g2.draw(new Line2D.Double(left, top + height, left + width, top + height));
        g2.draw(new Line2D.Double(left, top, left, top + height));

        Font font = getFont().deriveFont(Font.PLAIN, 11f);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();

        for (double xTick : xTicks.values()) {
            int x = valueToX(xTick, left, width, bounds);
            String label = xFormatter.format(xTick);
            int labelX = x - metrics.stringWidth(label) / 2;
            labelX = Math.max(4, Math.min(getWidth() - metrics.stringWidth(label) - 4, labelX));
            g2.drawString(label, labelX, top + height + 18);
        }

        for (double yTick : yTicks.values()) {
            int y = valueToY(yTick, top, height, bounds);
            String yLabelText = yFormatter.format(yTick);
            g2.drawString(yLabelText, left - metrics.stringWidth(yLabelText) - 8, y + metrics.getAscent() / 2);
        }
    }

    private void drawSeries(Graphics2D g2, int left, int top, int width, int height, Bounds bounds) {
        Stroke previousStroke = g2.getStroke();
        g2.setStroke(SERIES_STROKE);

        for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
            Series serie = series.get(seriesIndex);
            if (serie.points().isEmpty()) {
                continue;
            }

            g2.setColor(serie.color());
            Point previousPoint = null;
            for (GraphPoint point : serie.points()) {
                Point currentPoint = toScreen(point, left, top, width, height, bounds);
                if (serie.connectPoints() && previousPoint != null) {
                    g2.draw(new Line2D.Double(previousPoint.x, previousPoint.y, currentPoint.x, currentPoint.y));
                }
                drawMarker(g2, currentPoint, seriesIndex, serie.color());
                previousPoint = currentPoint;
            }
        }

        g2.setStroke(previousStroke);
    }

    private void drawPointLabels(Graphics2D g2, int left, int top, int width, int height, Bounds bounds) {
        Font labelFont = getFont().deriveFont(Font.PLAIN, 10f);
        g2.setFont(labelFont);
        g2.setColor(new Color(76, 88, 106));

        for (Series serie : series) {
            for (GraphPoint point : serie.points()) {
                if (point.label() == null || point.label().isBlank()) {
                    continue;
                }
                Point screen = toScreen(point, left, top, width, height, bounds);
                g2.drawString(point.label(), screen.x + 5, screen.y - 6);
            }
        }
    }

    private void drawMarker(Graphics2D g2, Point point, int seriesIndex, Color color) {
        double radius = 3.0 + (seriesIndex % 3) * 0.85;
        double diameter = radius * 2.0;
        double x = point.x - radius;
        double y = point.y - radius;

        g2.setColor(Color.WHITE);
        g2.fill(new Ellipse2D.Double(x, y, diameter, diameter));
        g2.setColor(color);
        g2.draw(new Ellipse2D.Double(x, y, diameter, diameter));
    }

    private void drawLegend(Graphics2D g2, int left, int top, int width) {
        int legendX = left + width - 180;
        int legendY = top + 8;
        Font font = getFont().deriveFont(Font.PLAIN, 11f);
        g2.setFont(font);

        int row = 0;
        for (Series serie : series) {
            if (serie.points().isEmpty()) {
                continue;
            }
            int y = legendY + row * 18;
            g2.setColor(serie.color());
            g2.fillRect(legendX, y - 10, 14, 10);
            g2.setColor(new Color(47, 57, 74));
            g2.drawString(serie.name(), legendX + 20, y);
            row++;
        }
    }

    private void drawLabels(Graphics2D g2, int left, int top, int width, int height, String xAxisLabel, String yAxisLabel) {
        Font font = getFont().deriveFont(Font.PLAIN, 12f);
        g2.setFont(font);
        g2.setColor(new Color(47, 57, 74));
        FontMetrics metrics = g2.getFontMetrics();

        g2.drawString(xAxisLabel, left + (width - metrics.stringWidth(xAxisLabel)) / 2, top + height + 40);

        Graphics2D rotated = (Graphics2D) g2.create();
        rotated.rotate(-Math.PI / 2);
        rotated.drawString(yAxisLabel, -(top + height / 2 + metrics.stringWidth(yAxisLabel) / 2), 18);
        rotated.dispose();
    }

    private Point toScreen(GraphPoint point, int left, int top, int width, int height, Bounds bounds) {
        return new Point(
            valueToX(point.x(), left, width, bounds),
            valueToY(point.y(), top, height, bounds)
        );
    }

    private int valueToX(double value, int left, int width, Bounds bounds) {
        double ratio = (value - bounds.minX) / (bounds.maxX - bounds.minX);
        return left + (int) Math.round(ratio * width);
    }

    private int valueToY(double value, int top, int height, Bounds bounds) {
        double ratio = (value - bounds.minY) / (bounds.maxY - bounds.minY);
        return top + height - (int) Math.round(ratio * height);
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {
    }
}
