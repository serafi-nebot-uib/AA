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
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Lightweight 2D graph panel for benchmark series.
 */
public final class BenchmarkGraphPanel extends JPanel {

    public record Series(String name, Color color, boolean connectPoints, List<Point2D.Double> points) {
        public Series {
            points = List.copyOf(points);
        }
    }

    private static final int LEFT_MARGIN = 64;
    private static final int RIGHT_MARGIN = 24;
    private static final int TOP_MARGIN = 44;
    private static final int BOTTOM_MARGIN = 54;
    private static final DecimalFormat AXIS_FORMAT = new DecimalFormat("0.###");
    private static final Stroke SERIES_STROKE = new BasicStroke(2.0f);

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

        Bounds bounds = computeBounds();
        int width = getWidth();
        int height = getHeight();
        int graphLeft = LEFT_MARGIN;
        int graphTop = TOP_MARGIN;
        int graphWidth = Math.max(120, width - LEFT_MARGIN - RIGHT_MARGIN);
        int graphHeight = Math.max(120, height - TOP_MARGIN - BOTTOM_MARGIN);

        drawGrid(g2, graphLeft, graphTop, graphWidth, graphHeight, bounds);
        drawAxes(g2, graphLeft, graphTop, graphWidth, graphHeight, bounds);
        drawSeries(g2, graphLeft, graphTop, graphWidth, graphHeight, bounds);
        drawLegend(g2, graphLeft, graphTop, graphWidth);
        drawLabels(g2, graphLeft, graphTop, graphWidth, graphHeight);

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
            for (Point2D.Double point : serie.points()) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
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
        return new Bounds(minX, maxX, Math.max(0.0, minY - yPadding), maxY + yPadding);
    }

    private void drawGrid(Graphics2D g2, int left, int top, int width, int height, Bounds bounds) {
        g2.setColor(new Color(232, 236, 242));
        for (int step = 0; step <= 5; step++) {
            int x = left + step * width / 5;
            int y = top + step * height / 5;
            g2.draw(new Line2D.Double(x, top, x, top + height));
            g2.draw(new Line2D.Double(left, y, left + width, y));
        }
    }

    private void drawAxes(Graphics2D g2, int left, int top, int width, int height, Bounds bounds) {
        g2.setColor(new Color(82, 90, 105));
        g2.draw(new Line2D.Double(left, top + height, left + width, top + height));
        g2.draw(new Line2D.Double(left, top, left, top + height));

        Font font = getFont().deriveFont(Font.PLAIN, 11f);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();

        for (int step = 0; step <= 5; step++) {
            double xValue = bounds.minX + (bounds.maxX - bounds.minX) * step / 5.0;
            int x = left + step * width / 5;
            String label = AXIS_FORMAT.format(xValue);
            g2.drawString(label, x - metrics.stringWidth(label) / 2, top + height + 18);

            double yValue = bounds.maxY - (bounds.maxY - bounds.minY) * step / 5.0;
            int y = top + step * height / 5;
            String yLabelText = AXIS_FORMAT.format(yValue);
            g2.drawString(yLabelText, left - metrics.stringWidth(yLabelText) - 8, y + metrics.getAscent() / 2);
        }
    }

    private void drawSeries(Graphics2D g2, int left, int top, int width, int height, Bounds bounds) {
        Stroke previousStroke = g2.getStroke();
        g2.setStroke(SERIES_STROKE);

        for (Series serie : series) {
            if (serie.points().isEmpty()) {
                continue;
            }

            g2.setColor(serie.color());
            Point previousPoint = null;
            for (Point2D.Double point : serie.points()) {
                Point currentPoint = toScreen(point, left, top, width, height, bounds);
                if (serie.connectPoints() && previousPoint != null) {
                    g2.draw(new Line2D.Double(previousPoint.x, previousPoint.y, currentPoint.x, currentPoint.y));
                }
                g2.fill(new Ellipse2D.Double(currentPoint.x - 3.5, currentPoint.y - 3.5, 7.0, 7.0));
                previousPoint = currentPoint;
            }
        }

        g2.setStroke(previousStroke);
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

    private void drawLabels(Graphics2D g2, int left, int top, int width, int height) {
        Font font = getFont().deriveFont(Font.PLAIN, 12f);
        g2.setFont(font);
        g2.setColor(new Color(47, 57, 74));
        FontMetrics metrics = g2.getFontMetrics();

        g2.drawString(xLabel, left + (width - metrics.stringWidth(xLabel)) / 2, top + height + 40);

        Graphics2D rotated = (Graphics2D) g2.create();
        rotated.rotate(-Math.PI / 2);
        rotated.drawString(yLabel, -(top + height / 2 + metrics.stringWidth(yLabel) / 2), 18);
        rotated.dispose();
    }

    private Point toScreen(Point2D.Double point, int left, int top, int width, int height, Bounds bounds) {
        double xRatio = (point.x - bounds.minX) / (bounds.maxX - bounds.minX);
        double yRatio = (point.y - bounds.minY) / (bounds.maxY - bounds.minY);
        int x = left + (int) Math.round(xRatio * width);
        int y = top + height - (int) Math.round(yRatio * height);
        return new Point(x, y);
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {
    }
}
