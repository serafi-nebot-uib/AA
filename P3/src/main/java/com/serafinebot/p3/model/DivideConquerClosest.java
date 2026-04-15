package com.serafinebot.p3.model;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Closest pair via Divide & Conquer in O(n log^2 n) with strip sorting per level.
 *
 * Binary-search for strip boundaries (O(log k) vs O(k) scan), sort strip by Y,
 * then check at most 7 forward neighbors.
 *
 * A single pre-allocated strip buffer is reused across all recursion levels,
 * eliminating per-level array allocations.
 *
 * Subclasses can override {@link #processStrip} to change the strip merge step
 * (template method pattern).
 */
public class DivideConquerClosest implements PairFinder {

    protected static final int BRUTE_FORCE_THRESHOLD = 3;

    @Override
    public PointPair find(Point[] points) {
        checkInterrupted();
        int n = points.length;
        if (n < 2) throw new IllegalArgumentException("Es necessiten almenys 2 punts");

        Point[] sortedByX = points.clone();
        Arrays.sort(sortedByX, Comparator.comparingDouble(Point::x));

        Point[] strip = new Point[n];
        return closestRec(sortedByX, 0, n - 1, strip);
    }

    private PointPair closestRec(Point[] px, int left, int right, Point[] strip) {
        checkInterrupted();
        int size = right - left + 1;

        if (size <= BRUTE_FORCE_THRESHOLD)
            return bruteForceRange(px, left, right);

        int mid = (left + right) / 2;
        double midX = px[mid].x();

        PointPair leftPair  = closestRec(px, left,    mid, strip);
        PointPair rightPair = closestRec(px, mid + 1, right, strip);
        PointPair best = leftPair.distance() <= rightPair.distance() ? leftPair : rightPair;

        double dMin = best.distance();

        // Build strip into pre-allocated buffer
        int stripLeft  = lowerBound(px, left,  right, midX - dMin);
        int stripRight = upperBound(px, left,  right, midX + dMin);
        int k = stripRight - stripLeft + 1;
        System.arraycopy(px, stripLeft, strip, 0, k);

        return processStrip(strip, k, dMin, best);
    }

    /**
     * Processes the strip to find a closer pair than {@code best}.
     * Default: sort by Y, check at most 7 forward neighbors.
     *
     * @param strip  pre-allocated buffer; only indices [0, k) are valid
     * @param k      number of points in the strip
     * @param dMin   current minimum distance
     * @param best   current best pair
     */
    protected PointPair processStrip(Point[] strip, int k, double dMin, PointPair best) {
        checkInterrupted();
        Arrays.sort(strip, 0, k, Comparator.comparingDouble(Point::y));

        for (int i = 0; i < k; i++) {
            if ((i & 63) == 0) checkInterrupted();
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
        checkInterrupted();
        PointPair best = new PointPair(points[left], points[left + 1]);
        double bestDist = points[left].distanceTo(points[left + 1]);

        for (int i = left; i <= right; i++) {
            if ((i & 63) == 0) checkInterrupted();
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

    protected static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException(new InterruptedException("Cancelled"));
        }
    }
}
