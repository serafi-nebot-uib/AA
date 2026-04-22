package com.serafinebot.p4.util;

/**
 * Shared byte-count formatter for CLI and Swing status text.
 */
public final class ByteFormat {

    private static final String[] UNITS = {"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};

    private ByteFormat() {
    }

    public static String format(long bytes) {
        double value = bytes;
        int unitIndex = 0;

        while (value >= 1024.0 && unitIndex < UNITS.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }

        if (unitIndex == 0) {
            return bytes + " " + UNITS[unitIndex];
        }
        return String.format("%.2f %s", value, UNITS[unitIndex]);
    }
}
