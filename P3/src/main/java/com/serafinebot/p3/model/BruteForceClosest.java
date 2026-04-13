package com.serafinebot.p3.model;

/**
 * Closest pair via exhaustive search in O(n²).
 * Checks every pair (i, j) with i < j and tracks the minimum distance found.
 */
public class BruteForceClosest implements PairFinder {

    @Override
    public PointPair find(Point[] points) {
        int n = points.length;
        if (n < 2) throw new IllegalArgumentException("Es necessiten almenys 2 punts");

        Point bestP1 = points[0];
        Point bestP2 = points[1];
        double bestDist = points[0].distanceTo(points[1]);

        for (int i = 0; i < n - 1; i++) {
            // Checked once per outer iteration so the O(n²) inner loop can be cancelled
            // cooperatively without excessive overhead from checking every inner step.
            if (Thread.currentThread().isInterrupted())
                throw new RuntimeException(new InterruptedException("Cancelled"));
            for (int j = i + 1; j < n; j++) {
                double dist = points[i].distanceTo(points[j]);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestP1 = points[i];
                    bestP2 = points[j];
                }
            }
        }

        return new PointPair(bestP1, bestP2);
    }
}
