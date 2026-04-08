package com.serafinebot.p3.model;

import java.util.Arrays;
import java.util.Comparator;

public class DivideConquerClosest {

    private static final int BRUTE_FORCE_THRESHOLD = 3;

    public static PointPair find(Point[] points) {
        int n = points.length;
        if (n < 2) throw new IllegalArgumentException("Es necessiten almenys 2 punts");

        Point[] sortedByX = points.clone();
        Arrays.sort(sortedByX, Comparator.comparingDouble(Point::x));

        return closestRec(sortedByX, 0, n - 1);
    }

    private static PointPair closestRec(Point[] px, int left, int right) {
        int size = right - left + 1;

        if (size <= BRUTE_FORCE_THRESHOLD) {
            return bruteForceRange(px, left, right);
        }

        int mid = (left + right) / 2;
        double midX = px[mid].x();

        PointPair leftPair = closestRec(px, left, mid);
        PointPair rightPair = closestRec(px, mid + 1, right);

        PointPair best;
        if (leftPair.distance() <= rightPair.distance()) {
            best = leftPair;
        } else {
            best = rightPair;
        }

        double dMin = best.distance();

        // Build strip: points within dMin of the dividing line
        Point[] strip = new Point[size];
        int stripSize = 0;
        for (int i = left; i <= right; i++) {
            if (Math.abs(px[i].x() - midX) < dMin) {
                strip[stripSize++] = px[i];
            }
        }

        // Sort strip by Y
        Arrays.sort(strip, 0, stripSize, Comparator.comparingDouble(Point::y));

        // Check at most 7 neighbors for each point in the strip
        for (int i = 0; i < stripSize; i++) {
            for (int j = i + 1; j < stripSize && (strip[j].y() - strip[i].y()) < dMin; j++) {
                double dist = strip[i].distanceTo(strip[j]);
                if (dist < dMin) {
                    dMin = dist;
                    best = new PointPair(strip[i], strip[j], dist);
                }
            }
        }

        return best;
    }

    private static PointPair bruteForceRange(Point[] points, int left, int right) {
        PointPair best = new PointPair(points[left], points[left + 1]);
        double bestDist = best.distance();

        for (int i = left; i <= right; i++) {
            for (int j = i + 1; j <= right; j++) {
                double dist = points[i].distanceTo(points[j]);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new PointPair(points[i], points[j], dist);
                }
            }
        }
        return best;
    }
}
