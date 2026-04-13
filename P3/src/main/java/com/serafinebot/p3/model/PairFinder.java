package com.serafinebot.p3.model;

/** Strategy interface for finding a notable point pair (closest or farthest) in a set. */
public interface PairFinder {
    PointPair find(Point[] points);
}
