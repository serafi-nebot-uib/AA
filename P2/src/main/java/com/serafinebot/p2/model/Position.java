package com.serafinebot.p2.model;

/**
 * Immutable record representing a position on the board.
 * Automatically generates equals(), hashCode(), and toString().
 */
public record Position(int row, int col) {
    public Position {
        if (row < 0 || col < 0) {
            throw new IllegalArgumentException(
                "Position coordinates must be non-negative: (" + row + "," + col + ")"
            );
        }
    }
}
