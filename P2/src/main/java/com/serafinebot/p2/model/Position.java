package com.serafinebot.p2.model;

/**
 * Immutable record representing a position on the board.
 * Automatically generates equals(), hashCode(), and toString().
 */
public record Position(int x, int y) {
    public Position {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException(
                "Position coordinates must be non-negative: (" + x + "," + y + ")"
            );
        }
    }

    public Position add(Position pos) {
        return new Position(x + pos.x, y + pos.y);
    }

    public Position mul(int a) {
        return new Position(x * a, y * a);
    }
}
