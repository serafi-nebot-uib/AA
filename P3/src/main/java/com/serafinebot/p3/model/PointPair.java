package com.serafinebot.p3.model;

public record PointPair(Point p1, Point p2, double distance) {

    public PointPair(Point p1, Point p2) {
        this(p1, p2, p1.distanceTo(p2));
    }

    @Override
    public String toString() {
        return String.format("%s - %s (d=%.6f)", p1, p2, distance);
    }
}
