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
        PointPair best = leftPair.distanceSq() <= rightPair.distanceSq() ? leftPair : rightPair;

        double dMinSq = best.distanceSq();
        double dMin   = best.distance();

        // Build strip: px is sorted by X, so points within dMin of midX are contiguous.
        // Binary search for the left and right boundaries, then slice directly.
        int stripLeft  = lowerBound(px, left,  right, midX - dMin);
        int stripRight = upperBound(px, left,  right, midX + dMin);
        Point[] strip = Arrays.copyOfRange(px, stripLeft, stripRight + 1);

        // Sort strip by Y
        Arrays.sort(strip, Comparator.comparingDouble(Point::y));

        // Check at most 7 neighbors for each point in the strip.
        // Use squared distance for comparisons — sqrt only when a better pair is found.
        int stripSize = strip.length;
        for (int i = 0; i < stripSize; i++) {
            for (int j = i + 1; j < stripSize && (strip[j].y() - strip[i].y()) < dMin; j++) {
                double distSq = strip[i].distanceSquaredTo(strip[j]);
                if (distSq < dMinSq) {
                    dMinSq = distSq;
                    dMin   = Math.sqrt(dMinSq); // keep dMin in sync for the Y-gap check above
                    best   = new PointPair(strip[i], strip[j]);
                }
            }
        }

        return best;
    }

    /** First index i in [left, right] where px[i].x() >= minX. */
    private static int lowerBound(Point[] px, int left, int right, double minX) {
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (px[mid].x() < minX) left = mid + 1;
            else right = mid;
        }
        return px[left].x() >= minX ? left : left + 1;
    }

    /** Last index i in [left, right] where px[i].x() <= maxX. */
    private static int upperBound(Point[] px, int left, int right, double maxX) {
        while (left < right) {
            int mid = (left + right + 1) >>> 1;
            if (px[mid].x() > maxX) right = mid - 1;
            else left = mid;
        }
        return px[right].x() <= maxX ? right : right - 1;
    }

    private static PointPair bruteForceRange(Point[] points, int left, int right) {
        PointPair best = new PointPair(points[left], points[left + 1]);
        double bestDistSq = points[left].distanceSquaredTo(points[left + 1]);

        for (int i = left; i <= right; i++) {
            for (int j = i + 1; j <= right; j++) {
                double distSq = points[i].distanceSquaredTo(points[j]);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = new PointPair(points[i], points[j]);
                }
            }
        }
        return best;
    }
}
