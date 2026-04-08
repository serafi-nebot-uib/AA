package com.serafinebot.p3.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FarthestPair {

    public static PointPair find(Point[] points) {
        int n = points.length;
        if (n < 2) throw new IllegalArgumentException("Es necessiten almenys 2 punts");
        if (n == 2) return new PointPair(points[0], points[1]);

        List<Point> hull = convexHull(points);
        return rotatingCalipers(hull);
    }

    /**
     * Andrew's monotone chain algorithm for convex hull. O(n log n)
     */
    private static List<Point> convexHull(Point[] points) {
        Point[] sorted = points.clone();
        Arrays.sort(sorted, Comparator.comparingDouble(Point::x)
                .thenComparingDouble(Point::y));

        int n = sorted.length;
        if (n <= 1) return List.of(sorted);

        List<Point> hull = new ArrayList<>(2 * n);

        // Lower hull
        for (Point p : sorted) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(p);
        }

        // Upper hull
        int lowerSize = hull.size() + 1;
        for (int i = n - 2; i >= 0; i--) {
            Point p = sorted[i];
            while (hull.size() >= lowerSize && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(p);
        }

        hull.remove(hull.size() - 1); // remove last (duplicate of first)
        return hull;
    }

    private static double cross(Point o, Point a, Point b) {
        return (a.x() - o.x()) * (b.y() - o.y())
             - (a.y() - o.y()) * (b.x() - o.x());
    }

    /**
     * Rotating calipers on convex hull to find the farthest pair. O(h) where h = hull size.
     */
    private static PointPair rotatingCalipers(List<Point> hull) {
        int h = hull.size();
        if (h == 1) throw new IllegalArgumentException("El convex hull necessita almenys 2 punts");
        if (h == 2) return new PointPair(hull.get(0), hull.get(1));

        // Find the point farthest from the edge (0, 1) to initialize
        int j = 1;
        while (triArea(hull.get(0), hull.get(1), hull.get((j + 1) % h))
             > triArea(hull.get(0), hull.get(1), hull.get(j))) {
            j++;
        }

        double bestDistSq = 0;
        Point bestP1 = null, bestP2 = null;

        for (int i = 0; i < h; i++) {
            Point a = hull.get(i);
            Point b = hull.get((i + 1) % h);

            while (triArea(a, b, hull.get((j + 1) % h)) > triArea(a, b, hull.get(j))) {
                j = (j + 1) % h;
            }

            double distSqI = a.distanceSquaredTo(hull.get(j));
            if (distSqI > bestDistSq) {
                bestDistSq = distSqI;
                bestP1 = a;
                bestP2 = hull.get(j);
            }

            double distSqI1 = b.distanceSquaredTo(hull.get(j));
            if (distSqI1 > bestDistSq) {
                bestDistSq = distSqI1;
                bestP1 = b;
                bestP2 = hull.get(j);
            }
        }

        return new PointPair(bestP1, bestP2);
    }

    private static double triArea(Point a, Point b, Point c) {
        return Math.abs((b.x() - a.x()) * (c.y() - a.y())
                      - (b.y() - a.y()) * (c.x() - a.x()));
    }
}
