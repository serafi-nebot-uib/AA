package com.serafinebot.p1.model;

import java.awt.Color;

/**
 * Enum que defineix els quatre tipus d'algorismes amb diferent cost computacional.
 * Cada algorisme implementa la seva execució i el càlcul del cost teòric.
 */
public enum AlgorithmType {

    LINEAR("O(n)", "Lineal", new Color(0, 100, 220)) {
        @Override
        public double theoreticalCost(long n) {
            return (double) n;
        }

        @Override
        public long execute(long n) {
            long sum = 0;
            for (long i = 0; i < n; i++) {
                if (Thread.currentThread().isInterrupted()) return 0;
                sum += i * i;
            }
            return sum;
        }
    },

    N_LOG_N("O(n·log n)", "N·Log N", new Color(0, 153, 0)) {
        @Override
        public double theoreticalCost(long n) {
            return n <= 1 ? 1.0 : n * (Math.log(n) / Math.log(2));
        }

        @Override
        public long execute(long n) {
            long sum = 0;
            for (long i = 0; i < n; i++) {
                if (Thread.currentThread().isInterrupted()) return 0;
                for (long j = 1; j < n; j *= 2) {
                    sum += i ^ j;
                }
            }
            return sum;
        }
    },

    QUADRATIC("O(n²)", "Quadràtic", new Color(220, 120, 0)) {
        @Override
        public double theoreticalCost(long n) {
            return (double) n * n;
        }

        @Override
        public long execute(long n) {
            long sum = 0;
            for (long i = 0; i < n; i++) {
                if (Thread.currentThread().isInterrupted()) return 0;
                for (long j = 0; j < n; j++) {
                    sum += i * j;
                }
            }
            return sum;
        }
    },

    CUBIC("O(n³)", "Cúbic", new Color(153, 0, 153)) {
        @Override
        public double theoreticalCost(long n) {
            return (double) n * n * n;
        }

        @Override
        public long execute(long n) {
            long sum = 0;
            for (long i = 0; i < n; i++) {
                for (long j = 0; j < n; j++) {
                    if (Thread.currentThread().isInterrupted()) return 0;
                    for (long k = 0; k < n; k++) {
                        sum += (i * j) + k;
                    }
                }
            }
            return sum;
        }
    };

    private final String notation;
    private final String displayName;
    private final Color color;

    AlgorithmType(String notation, String displayName, Color color) {
        this.notation = notation;
        this.displayName = displayName;
        this.color = color;
    }

    public String getNotation() { return notation; }
    public String getDisplayName() { return displayName; }
    public Color getColor() { return color; }

    public abstract double theoreticalCost(long n);
    public abstract long execute(long n);
}
