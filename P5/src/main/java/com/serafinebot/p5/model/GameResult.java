package com.serafinebot.p5.model;

import java.util.Arrays;
import java.util.List;

/**
 * Display-ready result returned by the controller after a successful solve.
 *
 * @param keypad         concrete keypad used by the solver
 * @param legalMoves     legal moves in the initial state
 * @param losingPlayer   1-based player number that loses under optimal play
 * @param winningPlayer  1-based winning player for two-player games, or 0 for N-player games
 * @param computedStates number of memoized states or table entries, depending on solver mode
 * @param replay         deterministic optimal-play line for the GUI replay controls
 */
public record GameResult(
        Keypad keypad,
        int[] legalMoves,
        int losingPlayer,
        int winningPlayer,
        int computedStates,
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
