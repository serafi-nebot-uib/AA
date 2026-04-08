package com.serafinebot.p3.model;

import java.util.Random;

public class PointCloud {

    public enum Distribution {
        UNIFORM("Uniforme"),
        GAUSSIAN("Gaussiana"),
        EXPONENTIAL("Exponencial"),
        CLUSTERED("Agrupada (Clusters)");

        private final String displayName;

        Distribution(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() { return displayName; }
    }

    private final Random rng;

    public PointCloud() {
        this.rng = new Random();
    }

    public PointCloud(long seed) {
        this.rng = new Random(seed);
    }

    public Point[] generate(int n, Distribution distribution, double rangeMin, double rangeMax) {
        return switch (distribution) {
            case UNIFORM -> generateUniform(n, rangeMin, rangeMax);
            case GAUSSIAN -> generateGaussian(n, rangeMin, rangeMax);
            case EXPONENTIAL -> generateExponential(n, rangeMin, rangeMax);
            case CLUSTERED -> generateClustered(n, rangeMin, rangeMax);
        };
    }

    private Point[] generateUniform(int n, double min, double max) {
        Point[] points = new Point[n];
        double range = max - min;
        for (int i = 0; i < n; i++) {
            points[i] = new Point(
                min + rng.nextDouble() * range,
                min + rng.nextDouble() * range
            );
        }
        return points;
    }

    private Point[] generateGaussian(int n, double min, double max) {
        Point[] points = new Point[n];
        double center = (min + max) / 2.0;
        double stddev = (max - min) / 6.0; // ~99.7% within range, <0.3% redrawn
        for (int i = 0; i < n; i++) {
            double x, y;
            do { x = center + rng.nextGaussian() * stddev; } while (x < min || x > max);
            do { y = center + rng.nextGaussian() * stddev; } while (y < min || y > max);
            points[i] = new Point(x, y);
        }
        return points;
    }

    private Point[] generateExponential(int n, double min, double max) {
        Point[] points = new Point[n];
        double lambda = 5.0 / (max - min); // mean = range/5, tail rarely exceeds range
        for (int i = 0; i < n; i++) {
            double x, y;
            do { x = min + (-Math.log(1 - rng.nextDouble()) / lambda); } while (x > max);
            do { y = min + (-Math.log(1 - rng.nextDouble()) / lambda); } while (y > max);
            points[i] = new Point(x, y);
        }
        return points;
    }

    private Point[] generateClustered(int n, double min, double max) {
        int numClusters = Math.max(3, (int) Math.sqrt(n / 10.0));
        Point[] centers = generateUniform(numClusters, min, max);
        double clusterRadius = (max - min) / (numClusters * 2.0);

        Point[] points = new Point[n];
        for (int i = 0; i < n; i++) {
            Point center = centers[rng.nextInt(numClusters)];
            double x = clamp(center.x() + rng.nextGaussian() * clusterRadius, min, max);
            double y = clamp(center.y() + rng.nextGaussian() * clusterRadius, min, max);
            points[i] = new Point(x, y);
        }
        return points;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
