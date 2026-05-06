package com.serafinebot.p5.model;

import java.util.Arrays;

/** One position in the deterministic optimal-play replay shown by the UI. */
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
}
