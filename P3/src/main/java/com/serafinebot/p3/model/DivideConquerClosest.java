package com.serafinebot.p3.model;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Closest pair via Divide & Conquer in O(n log n).
 *
 * Binary-search for strip boundaries (O(log k) vs O(k) scan), sort strip by Y,
 * then check at most 7 forward neighbors.
 *
 * Subclasses can override {@link #processStrip} to change the strip merge step
 * (template method pattern).
 */
public class DivideConquerClosest implements PairFinder {

    protected static final int BRUTE_FORCE_THRESHOLD = 3;

    @Override
    public PointPair find(Point[] points) {
        int n = points.length;
        if (n < 2) throw new IllegalArgumentException("Es necessiten almenys 2 punts");

        Point[] sortedByX = points.clone();
        Arrays.sort(sortedByX, Comparator.comparingDouble(Point::x));

        return closestRec(sortedByX, 0, n - 1);
    }

    private PointPair closestRec(Point[] px, int left, int right) {
        int size = right - left + 1;

        if (size <= BRUTE_FORCE_THRESHOLD)
            return bruteForceRange(px, left, right);

        int mid = (left + right) / 2;
        double midX = px[mid].x();

        PointPair leftPair  = closestRec(px, left,    mid);
        PointPair rightPair = closestRec(px, mid + 1, right);
        PointPair best = leftPair.distance() <= rightPair.distance() ? leftPair : rightPair;

        double dMin = best.distance();

        // Build strip — binary search for boundaries
        int stripLeft  = lowerBound(px, left,  right, midX - dMin);
        int stripRight = upperBound(px, left,  right, midX + dMin);
        Point[] strip  = Arrays.copyOfRange(px, stripLeft, stripRight + 1);

        return processStrip(strip, dMin, best);
    }

    /**
     * Processes the strip to find a closer pair than {@code best}.
     * Default: sort by Y, check at most 7 forward neighbors.
     */
    protected PointPair processStrip(Point[] strip, double dMin, PointPair best) {
        int k = strip.length;
        Arrays.sort(strip, Comparator.comparingDouble(Point::y));

        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k && (strip[j].y() - strip[i].y()) < dMin; j++) {
                double dist = strip[i].distanceTo(strip[j]);
                if (dist < dMin) {
                    dMin = dist;
                    best = new PointPair(strip[i], strip[j]);
                }
            }
        }

        return best;
    }

    /** First index i in [left, right] where px[i].x() >= minX. */
    protected static int lowerBound(Point[] px, int left, int right, double minX) {
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (px[mid].x() < minX) left = mid + 1;
            else right = mid;
        }
        return px[left].x() >= minX ? left : left + 1;
    }

    /** Last index i in [left, right] where px[i].x() <= maxX. */
    protected static int upperBound(Point[] px, int left, int right, double maxX) {
        while (left < right) {
            int mid = (left + right + 1) >>> 1;
            if (px[mid].x() > maxX) right = mid - 1;
            else left = mid;
        }
        return px[right].x() <= maxX ? right : right - 1;
    }

    protected static PointPair bruteForceRange(Point[] points, int left, int right) {
        PointPair best = new PointPair(points[left], points[left + 1]);
        double bestDist = points[left].distanceTo(points[left + 1]);

        for (int i = left; i <= right; i++) {
            for (int j = i + 1; j <= right; j++) {
                double dist = points[i].distanceTo(points[j]);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new PointPair(points[i], points[j]);
                }
            }
        }
        return best;
    }
}
