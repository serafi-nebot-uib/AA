package com.serafinebot.p3.model;


/**
 * Closest pair via Divide & Conquer with bucket optimisation for the strip merge step.
 *
 * Extends {@link DivideConquerClosest} and overrides only the strip processing.
 * Instead of sorting the strip by Y (O(k log k)), points are placed into fixed-height
 * buckets of size dMin in O(k). Each point then checks only its bucket and the one
 * immediately above — at most 8 candidates by the packing argument — giving an
 * O(k) strip step and reducing the overall recurrence from O(n log² n) to O(n log n).
 *
 * In practice the bucket path can be slower: dMin is tiny relative to the
 * Y range, so numBuckets >> k and the sparse-array allocation dominates.
 */
public class DivideConquerClosestBucket extends DivideConquerClosest {

    // Packing argument: a dMin × dMin cell per side holds at most 4 points,
    // so a full bucket (2dMin wide × dMin tall) holds at most 8.
    private static final int BUCKET_CAPACITY = 8;

    @Override
    protected PointPair processStrip(Point[] strip, int k, double dMin, PointPair best) {
        checkInterrupted();
        if (k < 2 || dMin <= 0) return best;

        // Compute bucket key for every strip point; track key range for offset indexing
        int[] keys   = new int[k];
        int minKey   = Integer.MAX_VALUE;
        int maxKey   = Integer.MIN_VALUE;
        for (int i = 0; i < k; i++) {
            if ((i & 255) == 0) checkInterrupted();
            int key = (int) Math.floor(strip[i].y() / dMin);
            keys[i] = key;
            if (key < minKey) minKey = key;
            if (key > maxKey) maxKey = key;
        }

        int numBuckets = maxKey - minKey + 1;

        // Flat bucket storage: buckets[b * BUCKET_CAPACITY + c] is the c-th point in bucket b.
        Point[] buckets = new Point[numBuckets * BUCKET_CAPACITY];
        int[]   counts  = new int[numBuckets];

        for (int i = 0; i < k; i++) {
            if ((i & 255) == 0) checkInterrupted();
            int b = keys[i] - minKey;
            buckets[b * BUCKET_CAPACITY + counts[b]++] = strip[i];
        }

        // For each point p check its bucket and the one above.
        for (int i = 0; i < k; i++) {
            if ((i & 255) == 0) checkInterrupted();
            Point p = strip[i];
            int   b = keys[i] - minKey;
            for (int dk = 0; dk <= 1; dk++) {
                int bk = b + dk;
                if (bk >= numBuckets) break;
                int base = bk * BUCKET_CAPACITY;
                for (int c = 0; c < counts[bk]; c++) {
                    Point q = buckets[base + c];
                    if (q == p) continue;
                    double dist = p.distanceTo(q);
                    if (dist < dMin) {
                        dMin = dist;
                        best = new PointPair(p, q);
                    }
                }
            }
        }

        return best;
    }
}
