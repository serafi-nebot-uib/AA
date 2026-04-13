package com.serafinebot.p3.view;

import com.serafinebot.p3.model.Benchmark;
import com.serafinebot.p3.model.Predictor;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.function.ToDoubleFunction;

/**
 * Panell de gràfica per a les dades de benchmark de P3.
 * Adaptat de GraphPanel de P1 (mateixa lògica d'eixos, graella i escala log).
 */
public class BenchmarkGraphPanel extends JPanel {

    /** View-only metadata for one plotted series. */
    public record Series(
            String name,
            String notation,
            Color color,
            ToDoubleFunction<Benchmark.BenchmarkEntry> getValue) {}

    /** The series in display order. Shared with BenchmarkWindow for checkbox labels. */
    public static final Series[] SERIES = {
        new Series("Força Bruta",             "O(n²)",       new Color(220, 60,  0),  e -> e.bruteForce().averageTimeMs()),
        new Series("Divideix i Venceràs",     "O(n·log^2 n)",  new Color(0,  140,  0),  e -> e.divideConquer().averageTimeMs()),
        new Series("Parella Més Llunyana",    "O(n·log n)",  new Color(0,   80, 200), e -> e.farthestPair().averageTimeMs()),
        new Series("D&C Bucket",              "O(n·log n)",  new Color(160,  0, 200), e -> e.divideConquerBucket().averageTimeMs()),
    };

    private Benchmark.BenchmarkEntry[] entries;
    private Predictor.Constants fittedConstants;
    private boolean logScale = false;
    private final boolean[] visible = {true, true, true, true};

    private static final int PAD_LEFT   = 75;
    private static final int PAD_RIGHT  = 25;
    private static final int PAD_TOP    = 25;
    private static final int PAD_BOTTOM = 50;

    public BenchmarkGraphPanel() {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createTitledBorder("Gràfica de Costos Computacionals"));
    }

    public void setData(Benchmark.BenchmarkEntry[] entries) {
        this.entries = entries;
        this.fittedConstants = null;
        repaint();
    }

    public void setFittedConstants(Predictor.Constants constants) {
        this.fittedConstants = constants;
        repaint();
    }

    public void setLogScale(boolean logScale) {
        this.logScale = logScale;
        repaint();
    }

    public void setSeriesVisible(int seriesIndex, boolean vis) {
        visible[seriesIndex] = vis;
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

        if (plotW <= 0 || plotH <= 0) { g2.dispose(); return; }

        if (entries == null || entries.length == 0) {
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("SansSerif", Font.ITALIC, 14));
            String msg = "Executeu el benchmark per veure la gràfica.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        // Compute data range
        double maxN = 0, maxTime = 0, minTimePos = Double.MAX_VALUE;
        for (Benchmark.BenchmarkEntry e : entries) {
            maxN = Math.max(maxN, e.n());
            for (int i = 0; i < SERIES.length; i++) {
                if (!visible[i]) continue;
                double t = SERIES[i].getValue().applyAsDouble(e);
                maxTime = Math.max(maxTime, t);
                if (t > 0) minTimePos = Math.min(minTimePos, t);
            }
        }

        maxN    *= 1.05;
        maxTime *= 1.1;
        if (maxTime == 0) maxTime = 1;
        if (minTimePos == Double.MAX_VALUE) minTimePos = 0.001;

        double logMin = Math.log10(minTimePos) - 0.5;
        double logMax = Math.log10(maxTime) + 0.3;

        drawAxesAndGrid(g2, plotW, plotH, maxN, maxTime, logMin, logMax);

        // Draw series
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < SERIES.length; i++) {
            if (!visible[i]) continue;
            g2.setColor(SERIES[i].color());
            int prevX = -1, prevY = -1;
            for (Benchmark.BenchmarkEntry e : entries) {
                int x = PAD_LEFT + (int) (plotW * e.n() / maxN);
                int y = computeY(SERIES[i].getValue().applyAsDouble(e), plotH, maxTime, logMin, logMax);
                y = Math.max(PAD_TOP, Math.min(PAD_TOP + plotH, y));
                if (prevX >= 0) g2.drawLine(prevX, prevY, x, y);
                g2.fillOval(x - 4, y - 4, 8, 8);
                prevX = x; prevY = y;
            }
        }

        // Draw fitted curves (dashed)
        if (fittedConstants != null) {
            drawFittedCurves(g2, plotW, plotH, maxN, maxTime, logMin, logMax);
        }

        drawLegend(g2);
        g2.dispose();
    }

    private void drawFittedCurves(Graphics2D g2, int plotW, int plotH,
                                   double maxN, double maxTime, double logMin, double logMax) {
        float[] dash = {6f, 4f};
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));

        int steps = plotW; // one sample per pixel column
        double[] constants = {
            fittedConstants.bruteForce(),
            fittedConstants.divideConquer(),
            fittedConstants.farthest(),
            fittedConstants.divideConquerBucket()
        };

        for (int i = 0; i < SERIES.length; i++) {
            if (!visible[i]) continue;
            g2.setColor(SERIES[i].color().darker());

            int prevX = -1, prevY = -1;
            for (int step = 0; step <= steps; step++) {
                double n = 2 + (maxN - 2) * step / steps;
                double t = switch (i) {
                    case 0 -> Predictor.predictBrute(constants[0], (long) n);
                    case 1 -> Predictor.predictDC(constants[1], (long) n);
                    case 2 -> Predictor.predictFarthest(constants[2], (long) n);
                    case 3 -> Predictor.predictBucket(constants[3], (long) n);
                    default -> 0;
                };
                int x = PAD_LEFT + (int) (plotW * n / maxN);
                int y = computeY(t, plotH, maxTime, logMin, logMax);

                if (y >= PAD_TOP && y <= PAD_TOP + plotH) {
                    if (prevX >= 0) g2.drawLine(prevX, prevY, x, y);
                    prevX = x;
                    prevY = y;
                } else {
                    // Out of bounds — break the curve so no horizontal line appears
                    prevX = -1;
                    prevY = -1;
                }
            }
        }
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

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(PAD_LEFT, PAD_TOP, PAD_LEFT, PAD_TOP + plotH);
        g2.drawLine(PAD_LEFT, PAD_TOP + plotH, PAD_LEFT + plotW, PAD_TOP + plotH);
        g2.setStroke(new BasicStroke(1f));

        // X ticks
        for (double val : generateRoundTicks(maxN, 10)) {
            int x = PAD_LEFT + (int) (plotW * val / maxN);
            g2.setColor(new Color(220, 220, 220));
            g2.drawLine(x, PAD_TOP, x, PAD_TOP + plotH);
            g2.setColor(Color.BLACK);
            g2.drawLine(x, PAD_TOP + plotH, x, PAD_TOP + plotH + 4);
            String label = formatN(val);
            g2.drawString(label, x - fm.stringWidth(label) / 2, PAD_TOP + plotH + 18);
        }

        // Y ticks
        if (logScale) {
            int floorMin = (int) Math.floor(logMin);
            int ceilMax  = (int) Math.ceil(logMax);
            double span  = logMax - logMin;
            for (int exp = floorMin; exp <= ceilMax; exp++) {
                double base = Math.pow(10, exp);
                if (span <= 6) {
                    for (double mult : new double[]{2, 5}) {
                        int y = computeY(base * mult, plotH, maxTime, logMin, logMax);
                        if (y < PAD_TOP || y > PAD_TOP + plotH) continue;
                        g2.setColor(new Color(235, 235, 235));
                        g2.drawLine(PAD_LEFT, y, PAD_LEFT + plotW, y);
                    }
                }
                int y = computeY(base, plotH, maxTime, logMin, logMax);
                if (y < PAD_TOP || y > PAD_TOP + plotH) continue;
                g2.setColor(new Color(220, 220, 220));
                g2.drawLine(PAD_LEFT, y, PAD_LEFT + plotW, y);
                g2.setColor(Color.BLACK);
                g2.drawLine(PAD_LEFT - 4, y, PAD_LEFT, y);
                String label = formatTime(base);
                g2.drawString(label, PAD_LEFT - fm.stringWidth(label) - 6, y + fm.getAscent() / 2);
            }
        } else {
            for (double val : generateRoundTicks(maxTime, 8)) {
                int y = computeY(val, plotH, maxTime, logMin, logMax);
                if (y < PAD_TOP || y > PAD_TOP + plotH) continue;
                g2.setColor(new Color(220, 220, 220));
                g2.drawLine(PAD_LEFT, y, PAD_LEFT + plotW, y);
                g2.setColor(Color.BLACK);
                g2.drawLine(PAD_LEFT - 4, y, PAD_LEFT, y);
                String label = formatTime(val);
                g2.drawString(label, PAD_LEFT - fm.stringWidth(label) - 6, y + fm.getAscent() / 2);
            }
        }

        // Axis labels
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
        int lineH   = 20;
        int legendH = SERIES.length * lineH + 10;
        int legendW = 240;

        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRoundRect(legendX, legendY, legendW, legendH, 8, 8);
        g2.setColor(Color.GRAY);
        g2.drawRoundRect(legendX, legendY, legendW, legendH, 8, 8);

        int y = legendY + lineH;
        for (Series s : SERIES) {
            g2.setColor(s.color());
            g2.fillOval(legendX + 8, y - 9, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawString(s.name() + " \u2014 " + s.notation(), legendX + 24, y);
            y += lineH;
        }
    }

    private String formatN(double val) {
        long r = Math.round(val);
        if (r >= 1_000_000) return String.format("%.1fM", r / 1_000_000.0).replace(".0M", "M");
        if (r >= 1_000)     return String.format("%.1fK", r / 1_000.0).replace(".0K", "K");
        return String.format("%d", r);
    }

    private String formatTime(double ms) {
        if (ms >= 60_000)  return String.format("%.1f min", ms / 60_000.0);
        if (ms >= 1_000)   return String.format("%.1f s",   ms / 1_000.0);
        if (ms >= 1)       return String.format("%.1f ms",  ms);
        if (ms >= 0.001)   return String.format("%.1f us",  ms * 1_000.0);
        return String.format("%.1f ns", ms * 1_000_000.0);
    }

    private double[] generateRoundTicks(double maxValue, int maxTicks) {
        if (maxValue <= 0) return new double[]{0};
        double step = maxValue / (maxTicks - 1);
        double magnitude = Math.pow(10, Math.floor(Math.log10(step)));
        double norm = step / magnitude;
        double roundNorm = norm <= 1.5 ? 1 : norm <= 3 ? 2 : norm <= 7 ? 5 : 10;
        double roundStep = roundNorm * magnitude;
        int numTicks = (int) Math.ceil(maxValue / roundStep) + 1;
        double[] ticks = new double[numTicks];
        for (int i = 0; i < numTicks; i++) ticks[i] = roundStep * i;
        return ticks;
    }
}
