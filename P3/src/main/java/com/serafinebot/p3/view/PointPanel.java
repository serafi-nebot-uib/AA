package com.serafinebot.p3.view;

import com.serafinebot.p3.model.Point;
import com.serafinebot.p3.model.PointPair;

import javax.swing.*;
import java.awt.*;

/**
 * Canvas that renders the current point cloud and highlights the closest pair
 * (red) and farthest pair (blue) once the algorithm results are available.
 */
public class PointPanel extends JPanel {

    private Point[] points;
    private PointPair closestPair;
    private PointPair farthestPair;
    private double rangeMin = 0;
    private double rangeMax = 1000;

    private static final int MARGIN = 30;
    private static final int POINT_SIZE = 4;

    public PointPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(600, 600));
    }

    public void setData(Point[] points, double rangeMin, double rangeMax) {
        this.points = points;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.closestPair = null;
        this.farthestPair = null;
        repaint();
    }

    public void setResults(PointPair closest, PointPair farthest) {
        this.closestPair = closest;
        this.farthestPair = farthest;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (points == null || points.length == 0) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth() - 2 * MARGIN;
        int h = getHeight() - 2 * MARGIN;

        // Draw all points
        g2.setColor(new Color(70, 70, 70));
        for (Point p : points) {
            int px = toScreenX(p.x(), w);
            int py = toScreenY(p.y(), h);
            g2.fillOval(px - POINT_SIZE / 2, py - POINT_SIZE / 2, POINT_SIZE, POINT_SIZE);
        }

        // Draw farthest pair (blue)
        if (farthestPair != null) {
            g2.setColor(new Color(0, 100, 255));
            g2.setStroke(new BasicStroke(2));
            drawPair(g2, farthestPair, w, h);
        }

        // Draw closest pair (red)
        if (closestPair != null) {
            g2.setColor(new Color(220, 30, 30));
            g2.setStroke(new BasicStroke(2));
            drawPair(g2, closestPair, w, h);
        }
    }

    private void drawPair(Graphics2D g2, PointPair pair, int w, int h) {
        int x1 = toScreenX(pair.p1().x(), w);
        int y1 = toScreenY(pair.p1().y(), h);
        int x2 = toScreenX(pair.p2().x(), w);
        int y2 = toScreenY(pair.p2().y(), h);

        g2.drawLine(x1, y1, x2, y2);

        int highlightSize = 8;
        g2.fillOval(x1 - highlightSize / 2, y1 - highlightSize / 2, highlightSize, highlightSize);
        g2.fillOval(x2 - highlightSize / 2, y2 - highlightSize / 2, highlightSize, highlightSize);
    }

    private int toScreenX(double x, int w) {
        return MARGIN + (int) ((x - rangeMin) / (rangeMax - rangeMin) * w);
    }

    private int toScreenY(double y, int h) {
        // Subtract from (MARGIN + h) to flip Y: data origin is bottom-left, screen is top-left.
        return MARGIN + h - (int) ((y - rangeMin) / (rangeMax - rangeMin) * h);
    }
}
