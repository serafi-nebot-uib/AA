package com.serafinebot.p1.view;

import com.serafinebot.p1.model.AlgorithmType;
import com.serafinebot.p1.model.Measurement;
import com.serafinebot.p1.model.Model;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;

/**
 * Panell personalitzat que dibuixa la grafica de costos computacionals.
 * Suporta escala lineal i logaritmica. Dibuixa eixos, graella, dades i llegenda.
 */
public class GraphPanel extends JPanel {

    private final Model model;
    private boolean logScale = false;

    private static final int PAD_LEFT = 75;
    private static final int PAD_RIGHT = 25;
    private static final int PAD_TOP = 25;
    private static final int PAD_BOTTOM = 50;

    public GraphPanel(Model model) {
        this.model = model;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createTitledBorder("Grafica de Costos Computacionals"));
    }

    public void setLogScale(boolean logScale) {
        this.logScale = logScale;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int plotW = w - PAD_LEFT - PAD_RIGHT;
        int plotH = h - PAD_TOP - PAD_BOTTOM;

        if (plotW <= 0 || plotH <= 0) {
            g2.dispose();
            return;
        }

        // Calcular rang de dades
        double maxN = 0, maxTime = 0, minTimePos = Double.MAX_VALUE;
        boolean hasData = false;

        for (AlgorithmType type : AlgorithmType.values()) {
            for (Measurement m : model.getMeasurements(type)) {
                hasData = true;
                maxN = Math.max(maxN, m.getN());
                double t = m.getTimeMs();
                maxTime = Math.max(maxTime, t);
                if (t > 0) minTimePos = Math.min(minTimePos, t);
            }
        }

        if (!hasData) {
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("SansSerif", Font.ITALIC, 14));
            String msg = "No hi ha dades. Executeu algun algorisme per comencar.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        // Marge extra
        maxN *= 1.05;
        maxTime *= 1.1;
        if (maxTime == 0) maxTime = 1;
        if (minTimePos == Double.MAX_VALUE) minTimePos = 0.001;

        double logMin = Math.log10(minTimePos) - 0.5;
        double logMax = Math.log10(maxTime) + 0.3;

        // Dibuixar graella i eixos
        drawAxesAndGrid(g2, plotW, plotH, maxN, maxTime, logMin, logMax);

        // Dibuixar series de dades
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (AlgorithmType type : AlgorithmType.values()) {
            List<Measurement> data = model.getMeasurements(type);
            if (data.isEmpty()) continue;

            g2.setColor(type.getColor());
            int prevX = -1, prevY = -1;

            for (Measurement m : data) {
                int x = PAD_LEFT + (int) (plotW * m.getN() / maxN);
                int y = computeY(m.getTimeMs(), plotH, maxTime, logMin, logMax);

                y = Math.max(PAD_TOP, Math.min(PAD_TOP + plotH, y));

                if (prevX >= 0) {
                    g2.drawLine(prevX, prevY, x, y);
                }
                g2.fillOval(x - 4, y - 4, 8, 8);
                prevX = x;
                prevY = y;
            }
        }

        // Llegenda
        drawLegend(g2);
        g2.dispose();
    }

    private int computeY(double timeMs, int plotH, double maxTime, double logMin, double logMax) {
        if (logScale) {
            if (timeMs <= 0) return PAD_TOP + plotH;
            double logT = Math.log10(timeMs);
            return PAD_TOP + plotH - (int) (plotH * (logT - logMin) / (logMax - logMin));
        } else {
            return PAD_TOP + plotH - (int) (plotH * timeMs / maxTime);
        }
    }

    private void drawAxesAndGrid(Graphics2D g2, int plotW, int plotH,
                                  double maxN, double maxTime, double logMin, double logMax) {
        int h = getHeight();

        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        FontMetrics fm = g2.getFontMetrics();

        // Eixos
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(PAD_LEFT, PAD_TOP, PAD_LEFT, PAD_TOP + plotH);
        g2.drawLine(PAD_LEFT, PAD_TOP + plotH, PAD_LEFT + plotW, PAD_TOP + plotH);

        g2.setStroke(new BasicStroke(1f));

        // Marques eix X - valors exactes i rodons
        double[] xTicks = generateRoundTicks(maxN, 10);
        for (int i = 0; i < xTicks.length; i++) {
            double val = xTicks[i];
            int x = PAD_LEFT + (int) (plotW * val / maxN);
            g2.setColor(new Color(220, 220, 220));
            g2.drawLine(x, PAD_TOP, x, PAD_TOP + plotH);
            g2.setColor(Color.BLACK);
            g2.drawLine(x, PAD_TOP + plotH, x, PAD_TOP + plotH + 4);
            String label = formatN(val);
            g2.drawString(label, x - fm.stringWidth(label) / 2, PAD_TOP + plotH + 18);
        }

        // Marques eix Y - valors exactes i rodons
        double[] yTicks = generateRoundTicks(maxTime, 8);
        for (int i = 0; i < yTicks.length; i++) {
            double val = yTicks[i];
            int y = computeY(val, plotH, maxTime, logMin, logMax);
            g2.setColor(new Color(220, 220, 220));
            g2.drawLine(PAD_LEFT, y, PAD_LEFT + plotW, y);
            g2.setColor(Color.BLACK);
            g2.drawLine(PAD_LEFT - 4, y, PAD_LEFT, y);

            String label;
            if (logScale) {
                label = formatTime(val);
            } else {
                label = formatTime(val);
            }
            g2.drawString(label, PAD_LEFT - fm.stringWidth(label) - 6, y + fm.getAscent() / 2);
        }

        // Etiquetes dels eixos
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(Color.BLACK);
        fm = g2.getFontMetrics();
        g2.drawString("n", PAD_LEFT + plotW / 2 - 3, h - 5);

        AffineTransform orig = g2.getTransform();
        g2.rotate(-Math.PI / 2);
        String yLabel = logScale ? "Temps (ms, log)" : "Temps (ms)";
        g2.drawString(yLabel, -(PAD_TOP + plotH / 2 + fm.stringWidth(yLabel) / 2), 14);
        g2.setTransform(orig);
    }

    private void drawLegend(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int legendX = PAD_LEFT + 15;
        int legendY = PAD_TOP + 8;
        int lineH = 20;
        AlgorithmType[] types = AlgorithmType.values();
        int legendH = types.length * lineH + 10;
        int legendW = 180;

        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRoundRect(legendX, legendY, legendW, legendH, 8, 8);
        g2.setColor(Color.GRAY);
        g2.drawRoundRect(legendX, legendY, legendW, legendH, 8, 8);

        int y = legendY + lineH;
        for (AlgorithmType type : types) {
            g2.setColor(type.getColor());
            g2.fillOval(legendX + 8, y - 9, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawString(type.getDisplayName() + " \u2014 " + type.getNotation(), legendX + 24, y);
            y += lineH;
        }
    }

    private String formatN(double val) {
        long rounded = Math.round(val);
        if (rounded >= 1_000_000) {
            double millions = rounded / 1_000_000.0;
            if (millions == Math.floor(millions)) {
                return String.format("%dM", (long)millions);
            } else {
                return String.format("%.1fM", millions);
            }
        }
        if (rounded >= 1_000) {
            double thousands = rounded / 1_000.0;
            if (thousands == Math.floor(thousands)) {
                return String.format("%dK", (long)thousands);
            } else {
                return String.format("%.1fK", thousands);
            }
        }
        return String.format("%d", rounded);
    }

    private String formatTime(double ms) {
        if (ms >= 60_000) {
            long mins = Math.round(ms / 60_000);
            return mins + " min";
        }
        if (ms >= 1_000) {
            long secs = Math.round(ms / 1_000);
            return secs + " s";
        }
        if (ms >= 1) {
            return String.format("%d ms", Math.round(ms));
        }
        if (ms >= 0.001) {
            long micros = Math.round(ms * 1_000);
            return micros + " us";
        }
        long nanos = Math.round(ms * 1_000_000);
        return nanos + " ns";
    }

    private double[] generateRoundTicks(double maxValue, int maxTicks) {
        if (maxValue <= 0) return new double[]{0};
        
        double step = maxValue / (maxTicks - 1);
        double magnitude = Math.pow(10, Math.floor(Math.log10(step)));
        
        double normalizedStep = step / magnitude;
        double roundNormalizedStep;
        
        if (normalizedStep <= 1.5) {
            roundNormalizedStep = 1;
        } else if (normalizedStep <= 3) {
            roundNormalizedStep = 2;
        } else if (normalizedStep <= 7) {
            roundNormalizedStep = 5;
        } else {
            roundNormalizedStep = 10;
        }
        
        double roundStep = roundNormalizedStep * magnitude;
        int numTicks = (int) Math.ceil(maxValue / roundStep) + 1;
        double[] ticks = new double[numTicks];
        
        for (int i = 0; i < numTicks; i++) {
            ticks[i] = roundStep * i;
        }
        
        return ticks;
    }
}
