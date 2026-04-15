package com.serafinebot.p3.model;

import java.util.Random;

/**
 * Generates 2-D point clouds under various statistical distributions.
 * Each distribution is parameterised via the {@link ParamSpec} array declared
 * on the corresponding {@link Distribution} enum constant.
 */
public class PointCloud {

    private final Random rng;

    public PointCloud() {
        this.rng = new Random();
    }

    public PointCloud(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * Generates n points according to the given distribution.
     * {@code params.get(i)} corresponds to {@code distribution.params()[i]}.
     */
    public Point[] generate(int n, Distribution distribution, double rangeMin, double rangeMax,
                            DistributionParams params) {
        return switch (distribution) {
            case UNIFORM     -> generateUniform(n, rangeMin, rangeMax);
            case GAUSSIAN    -> generateGaussian(n, rangeMin, rangeMax, params);
            case EXPONENTIAL -> generateExponential(n, rangeMin, rangeMax, params.get(0), params.get(1));
            case CLUSTERED   -> generateClustered(n, rangeMin, rangeMax, (int) Math.max(3, params.get(0)));
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

    // params: [0]=mean (fraction of range), [1]=σ (fraction of range), [2]=multX, [3]=multY
    private Point[] generateGaussian(int n, double min, double max, DistributionParams params) {
        double range   = max - min;
        double centerX = min + params.get(0) * range;
        double centerY = centerX;
        double sigmaX  = params.get(1) * range * params.get(2);
        double sigmaY  = params.get(1) * range * params.get(3);

        Point[] points = new Point[n];
        for (int i = 0; i < n; i++) {
            double x, y;
            do { x = centerX + rng.nextGaussian() * sigmaX; } while (x < min || x > max);
            do { y = centerY + rng.nextGaussian() * sigmaY; } while (y < min || y > max);
            points[i] = new Point(x, y);
        }
        return points;
    }

    // params: [0]=factorX, [1]=factorY  (mean_axis = range / factor_axis)
    private Point[] generateExponential(int n, double min, double max, double factorX, double factorY) {
        double range   = max - min;
        double lambdaX = factorX / range;
        double lambdaY = factorY / range;
        Point[] points = new Point[n];
        for (int i = 0; i < n; i++) {
            double x, y;
            // Inverse-CDF sampling with rejection to clamp to [min, max].
            // Samples very rarely exceed max when factor is small (fast decay).
            do { x = min + (-Math.log(1 - rng.nextDouble()) / lambdaX); } while (x > max);
            do { y = min + (-Math.log(1 - rng.nextDouble()) / lambdaY); } while (y > max);
            points[i] = new Point(x, y);
        }
        return points;
    }

    // params: [0]=numClusters
    private Point[] generateClustered(int n, double min, double max, int numClusters) {
        Point[] centers = generateUniform(numClusters, min, max);
        double clusterRadius = (max - min) / (numClusters * 2.0);

        Point[] points = new Point[n];
        for (int i = 0; i < n; i++) {
            Point center = centers[rng.nextInt(numClusters)];
            double x, y;
            do { x = center.x() + rng.nextGaussian() * clusterRadius; } while (x < min || x > max);
            do { y = center.y() + rng.nextGaussian() * clusterRadius; } while (y < min || y > max);
            points[i] = new Point(x, y);
        }
        return points;
    }
}
