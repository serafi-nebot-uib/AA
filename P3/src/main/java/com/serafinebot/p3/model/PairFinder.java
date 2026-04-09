package com.serafinebot.p3.model;

@FunctionalInterface
public interface PairFinder {
    PointPair find(Point[] points);
}
