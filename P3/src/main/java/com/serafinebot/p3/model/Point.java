package com.serafinebot.p3.model;

public record Point(double x, double y) {

    public double distanceTo(Point other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceSquaredTo(Point other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return dx * dx + dy * dy;
    }

    @Override
    public String toString() {
        return String.format("(%.4f, %.4f)", x, y);
    }
}
