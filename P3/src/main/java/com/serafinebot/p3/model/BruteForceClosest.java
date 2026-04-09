package com.serafinebot.p3.model;

public class BruteForceClosest {

    public static PointPair find(Point[] points) {
        int n = points.length;
        if (n < 2) throw new IllegalArgumentException("Es necessiten almenys 2 punts");

        Point bestP1 = points[0];
        Point bestP2 = points[1];
        double bestDistSq = points[0].distanceSquaredTo(points[1]);

        for (int i = 0; i < n - 1; i++) {
            if (Thread.currentThread().isInterrupted())
                throw new RuntimeException(new InterruptedException("Cancelled"));
            for (int j = i + 1; j < n; j++) {
                double distSq = points[i].distanceSquaredTo(points[j]);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestP1 = points[i];
                    bestP2 = points[j];
                }
            }
        }

        return new PointPair(bestP1, bestP2);
    }
}
