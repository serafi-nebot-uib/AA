package com.serafinebot.p3.model;

public class PointPair {

    public final Point p1;
    public final Point p2;

    private double distanceSq = -1;
    private double distance   = -1;

    public PointPair(Point p1, Point p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public Point p1() { return p1; }
    public Point p2() { return p2; }

    public double distanceSq() {
        if (distanceSq < 0) distanceSq = p1.distanceSquaredTo(p2);
        return distanceSq;
    }

    public double distance() {
        if (distance < 0) distance = Math.sqrt(distanceSq());
        return distance;
    }

    @Override
    public String toString() {
        return String.format("%s - %s (d=%.6f)", p1, p2, distance());
    }
}
