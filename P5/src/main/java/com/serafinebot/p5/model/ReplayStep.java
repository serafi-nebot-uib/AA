package com.serafinebot.p5.model;

import java.util.Arrays;

/**
 * One position in the deterministic optimal-play replay shown by the UI.
 *
 * <p>Step 0 is the initial position and therefore has no moved player or move.
 * Later steps describe the move that produced the new total. Player numbers in
 * replay data are 1-based because the values are displayed directly.
 */
public record ReplayStep(
        int index,
        int totalBefore,
        int total,
        int lastPlayed,
        int movedPlayer,
        int move,
        int currentPlayer,
        int[] legalMoves,
        boolean terminal
) {
    public ReplayStep {
        legalMoves = Arrays.copyOf(legalMoves, legalMoves.length);
    }

    public int[] legalMoves() {
        return Arrays.copyOf(legalMoves, legalMoves.length);
    }

    public boolean initial() {
        return index == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ReplayStep other)) return false;
        return index == other.index
                && totalBefore == other.totalBefore
                && total == other.total
                && lastPlayed == other.lastPlayed
                && movedPlayer == other.movedPlayer
                && move == other.move
                && currentPlayer == other.currentPlayer
                && terminal == other.terminal
                && Arrays.equals(legalMoves, other.legalMoves);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(index);
        result = 31 * result + Integer.hashCode(totalBefore);
        result = 31 * result + Integer.hashCode(total);
        result = 31 * result + Integer.hashCode(lastPlayed);
        result = 31 * result + Integer.hashCode(movedPlayer);
        result = 31 * result + Integer.hashCode(move);
        result = 31 * result + Integer.hashCode(currentPlayer);
        result = 31 * result + Boolean.hashCode(terminal);
        result = 31 * result + Arrays.hashCode(legalMoves);
        return result;
    }
}
