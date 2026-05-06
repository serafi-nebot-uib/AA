package com.serafinebot.p5.model;

import java.util.Arrays;
import java.util.List;

/** Display-ready result returned by the controller after a successful solve. */
public record GameResult(
        Keypad keypad,
        int[] legalMoves,
        int losingPlayer,
        int winningPlayer,
        int memoizedStates,
        List<ReplayStep> replay
) {
    public GameResult {
        legalMoves = Arrays.copyOf(legalMoves, legalMoves.length);
        replay = List.copyOf(replay);
    }

    public int[] legalMoves() {
        return Arrays.copyOf(legalMoves, legalMoves.length);
    }
}
