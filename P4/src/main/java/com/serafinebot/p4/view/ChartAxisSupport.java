package com.serafinebot.p4.view;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for axis tick generation and value formatting.
 */
final class ChartAxisSupport {

    private static final DecimalFormat AXIS_VALUE_FORMAT = new DecimalFormat("0.###");
    private static final String[] BYTE_UNITS = {"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
    private static final double BYTE_BASE = 1024.0;

    private ChartAxisSupport() {
    }

    static AxisTicks computeTicks(double min, double max, int targetTickCount) {
        return computeNiceTicks(min, max, targetTickCount);
    }

    private static AxisTicks computeNiceTicks(double min, double max, int targetTickCount) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return new AxisTicks(0.0, 1.0, 1.0, List.of(0.0, 1.0));
        }

        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }

        if (Double.compare(min, max) == 0) {
            double delta = Math.abs(min) < 1.0 ? 1.0 : Math.abs(min) * 0.2;
            min -= delta;
            max += delta;
        }

        int safeTickCount = Math.max(3, targetTickCount);
        double range = niceNumber(max - min, false);
        double step = niceNumber(range / (safeTickCount - 1.0), true);
        if (step <= 0.0 || !Double.isFinite(step)) {
            step = 1.0;
        }

        double niceMin = Math.floor(min / step) * step;
        double niceMax = Math.ceil(max / step) * step;
        List<Double> values = new ArrayList<>();
        double epsilon = step * 1e-6;
        for (double value = niceMin; value <= niceMax + epsilon; value += step) {
            values.add(normalizeZero(value));
            if (values.size() > 1024) {
                break;
            }
        }
        if (values.size() < 2) {
            values = List.of(normalizeZero(niceMin), normalizeZero(niceMax));
        }

        return new AxisTicks(normalizeZero(niceMin), normalizeZero(niceMax), step, values);
    }

    static int suggestTickCount(int pixelLength) {
        int estimate = Math.max(4, pixelLength / 110);
        return Math.min(10, estimate);
    }

    static AxisValueFormatter formatter(String axisLabel, double min, double max) {
        return new AxisValueFormatter(axisLabel, min, max);
    }

    private static double niceNumber(double value, boolean round) {
        if (value <= 0.0 || !Double.isFinite(value)) {
            return 1.0;
        }

        double exponent = Math.floor(Math.log10(value));
        double fraction = value / Math.pow(10.0, exponent);
        double niceFraction;

        if (round) {
            if (fraction < 1.5) {
                niceFraction = 1.0;
            } else if (fraction < 3.0) {
                niceFraction = 2.0;
            } else if (fraction < 7.0) {
                niceFraction = 5.0;
            } else {
                niceFraction = 10.0;
            }
        } else if (fraction <= 1.0) {
            niceFraction = 1.0;
        } else if (fraction <= 2.0) {
            niceFraction = 2.0;
        } else if (fraction <= 5.0) {
            niceFraction = 5.0;
        } else {
            niceFraction = 10.0;
        }

        return niceFraction * Math.pow(10.0, exponent);
    }

    private static double normalizeZero(double value) {
        return Math.abs(value) < 1e-10 ? 0.0 : value;
    }

    record AxisTicks(double min, double max, double step, List<Double> values) {
        AxisTicks {
            values = List.copyOf(values);
        }
    }

    static final class AxisValueFormatter {

        private final ValueStyle style;
        private final int fixedByteUnitIndex;

        private AxisValueFormatter(String axisLabel, double min, double max) {
            String normalized = axisLabel == null ? "" : axisLabel.toLowerCase(Locale.ROOT);
            this.style = normalized.contains("byte") ? ValueStyle.BYTES : ValueStyle.METRIC;
            this.fixedByteUnitIndex = style == ValueStyle.BYTES
                ? selectByteUnitIndex(Math.max(Math.abs(min), Math.abs(max)))
                : 0;
        }

        String format(double value) {
            return style == ValueStyle.BYTES ? formatBytes(value) : formatMetric(value);
        }

        AxisTicks ticks(double min, double max, int targetTickCount) {
            if (style != ValueStyle.BYTES) {
                return computeNiceTicks(min, max, targetTickCount);
            }

            double factor = byteUnitFactor();
            AxisTicks scaledTicks = computeNiceTicks(min / factor, max / factor, targetTickCount);
            List<Double> rescaledValues = new ArrayList<>(scaledTicks.values().size());
            for (double value : scaledTicks.values()) {
                rescaledValues.add(normalizeZero(value * factor));
            }

            return new AxisTicks(
                normalizeZero(scaledTicks.min() * factor),
                normalizeZero(scaledTicks.max() * factor),
                scaledTicks.step() * factor,
                rescaledValues
            );
        }

        String axisLabel(String originalLabel) {
            if (style != ValueStyle.BYTES || originalLabel == null || originalLabel.isBlank()) {
                return originalLabel;
            }

            String unit = BYTE_UNITS[fixedByteUnitIndex];
            int open = originalLabel.indexOf('(');
            int close = originalLabel.indexOf(')', open + 1);
            if (open >= 0 && close > open) {
                return originalLabel.substring(0, open + 1) + unit + originalLabel.substring(close);
            }
            return originalLabel + " (" + unit + ')';
        }

        private String formatBytes(double value) {
            double scaledValue = value / byteUnitFactor();
            return AXIS_VALUE_FORMAT.format(scaledValue);
        }

        private double byteUnitFactor() {
            return Math.pow(BYTE_BASE, fixedByteUnitIndex);
        }

        private String formatMetric(double value) {
            return AXIS_VALUE_FORMAT.format(value);
        }

        private static int selectByteUnitIndex(double absoluteValue) {
            int unitIndex = 0;
            while (absoluteValue >= BYTE_BASE && unitIndex < BYTE_UNITS.length - 1) {
                absoluteValue /= BYTE_BASE;
                unitIndex++;
            }
            return unitIndex;
        }
    }

    private enum ValueStyle {
        BYTES,
        METRIC
    }
}
