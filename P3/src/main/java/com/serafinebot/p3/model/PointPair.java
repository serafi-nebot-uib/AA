package com.serafinebot.p3.model;

/** An ordered pair of points with lazily-computed distance. */
public class PointPair {

    public final Point p1;
    public final Point p2;

    // Sentinel -1 means "not yet computed". Valid distances are always >= 0,
    // so -1 is a safe uninitialized marker.
    private double distance   = -1;

    public PointPair(Point p1, Point p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public Point p1() { return p1; }
    public Point p2() { return p2; }

    public double distance() {
        if (distance < 0) {
            double dx = p1.x() - p2.x();
            double dy = p1.y() - p2.y();
            distance = Math.sqrt(dx * dx + dy * dy);
        }
        return distance;
    }

    @Override
    public String toString() {
        return String.format("%s - %s (d=%.6f)", p1, p2, distance());
    }
}
