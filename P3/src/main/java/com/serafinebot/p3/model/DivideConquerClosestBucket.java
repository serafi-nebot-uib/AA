package com.serafinebot.p3.model;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Closest pair via Divide & Conquer with bucket optimisation for the strip merge step.
 *
 * Instead of sorting the strip by Y (O(k log k)), points are placed into fixed-height
 * buckets of size dMin in O(k). Each point then checks only the bucket it lands in and
 * the one immediately above — at most 8 candidates by the packing argument — giving an
 * O(k) strip step and reducing the overall recurrence from O(n log² n) to O(n log n).
 *
 * Buckets are stored as a flat Point[] with stride 8 (the theoretical max per bucket)
 * and a parallel int[] for per-bucket counts. No HashMap, no ArrayList, no boxing.
 */
public class DivideConquerClosestBucket {

    private static final int BRUTE_FORCE_THRESHOLD = 3;
    // Packing argument: a dMin × dMin cell per side holds at most 4 points,
    // so a full bucket (2dMin wide × dMin tall) holds at most 8.
    private static final int BUCKET_CAPACITY = 8;

    public static PointPair find(Point[] points) {
        int n = points.length;
        if (n < 2) throw new IllegalArgumentException("Es necessiten almenys 2 punts");

        Point[] sortedByX = points.clone();
        Arrays.sort(sortedByX, Comparator.comparingDouble(Point::x));

        return closestRec(sortedByX, 0, n - 1);
    }

    private static PointPair closestRec(Point[] px, int left, int right) {
        int size = right - left + 1;

        if (size <= BRUTE_FORCE_THRESHOLD)
            return bruteForceRange(px, left, right);

        int mid = (left + right) / 2;
        double midX = px[mid].x();

        PointPair leftPair  = closestRec(px, left,    mid);
        PointPair rightPair = closestRec(px, mid + 1, right);
        PointPair best = leftPair.distanceSq() <= rightPair.distanceSq() ? leftPair : rightPair;

        double dMinSq = best.distanceSq();
        double dMin   = best.distance();

        // Build strip using binary search — O(log size)
        int stripLeft  = lowerBound(px, left, right, midX - dMin);
        int stripRight = upperBound(px, left, right, midX + dMin);
        Point[] strip  = Arrays.copyOfRange(px, stripLeft, stripRight + 1);
        int     k      = strip.length;

        // Compute bucket key for every strip point; track key range for offset indexing
        int[] keys   = new int[k];
        int minKey   = Integer.MAX_VALUE;
        int maxKey   = Integer.MIN_VALUE;
        for (int i = 0; i < k; i++) {
            int key = (int) Math.floor(strip[i].y() / dMin);
            keys[i] = key;
            if (key < minKey) minKey = key;
            if (key > maxKey) maxKey = key;
        }

        // Flat bucket storage: buckets[b * BUCKET_CAPACITY + c] is the c-th point in bucket b.
        // counts[b] tracks how many points have been placed in bucket b.
        //
        // Guard: if numBuckets > k the array would be sparser than O(1) per bucket, hurting both
        // allocation cost and cache locality. In that case fall back to the sort-based scan which
        // is O(k log k) — cheaper than touching O(numBuckets) mostly-empty memory.
        int numBuckets = maxKey - minKey + 1;
        if (numBuckets > k) {
            // Sort strip by Y then check at most 7 forward neighbors
            Arrays.sort(strip, Comparator.comparingDouble(Point::y));
            for (int i = 0; i < k; i++) {
                Point p = strip[i];
                for (int j = i + 1; j < k && strip[j].y() - p.y() < dMin; j++) {
                    double distSq = p.distanceSquaredTo(strip[j]);
                    if (distSq < dMinSq) {
                        dMinSq = distSq;
                        dMin   = Math.sqrt(dMinSq);
                        best   = new PointPair(p, strip[j]);
                    }
                }
            }
            return best;
        }

        Point[] buckets = new Point[numBuckets * BUCKET_CAPACITY];
        int[]   counts  = new int[numBuckets];

        for (int i = 0; i < k; i++) {
            int b = keys[i] - minKey;
            buckets[b * BUCKET_CAPACITY + counts[b]++] = strip[i];
        }

        // For each point p check its bucket and the one above.
        // Any candidate q with |p.y - q.y| < dMin must be in bucket b or b+1:
        //   if q.y >= p.y → q.y < p.y + dMin < (b+1)·dMin + dMin = (b+2)·dMin → bucket ≤ b+1
        //   if q.y <  p.y → p is in bucket b+1 relative to q; caught when q is outer point
        for (int i = 0; i < k; i++) {
            Point p = strip[i];
            int   b = keys[i] - minKey;
            for (int dk = 0; dk <= 1; dk++) {
                int bk = b + dk;
                if (bk >= numBuckets) break;
                int base = bk * BUCKET_CAPACITY;
                for (int c = 0; c < counts[bk]; c++) {
                    Point q = buckets[base + c];
                    if (q == p) continue;
                    double distSq = p.distanceSquaredTo(q);
                    if (distSq < dMinSq) {
                        dMinSq = distSq;
                        dMin   = Math.sqrt(dMinSq);
                        best   = new PointPair(p, q);
                    }
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
        PointPair best       = new PointPair(points[left], points[left + 1]);
        double    bestDistSq = points[left].distanceSquaredTo(points[left + 1]);

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
