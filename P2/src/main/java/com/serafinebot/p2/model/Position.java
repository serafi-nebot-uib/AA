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
    
    /**
     * Calculate Manhattan distance to another position.
     * @param other The other position
     * @return Manhattan distance (|Δrow| + |Δcol|)
     */
    public int distance(Position other) {
        return Math.abs(this.row - other.row) + Math.abs(this.col - other.col);
    }
    
    /**
     * Check if this position is adjacent (including diagonals) to another.
     * @param other The other position
     * @return true if adjacent (8 directions), false otherwise
     */
    public boolean isAdjacentTo(Position other) {
        int dr = Math.abs(this.row - other.row);
        int dc = Math.abs(this.col - other.col);
        return (dr <= 1 && dc <= 1) && (dr + dc > 0);
    }
}
