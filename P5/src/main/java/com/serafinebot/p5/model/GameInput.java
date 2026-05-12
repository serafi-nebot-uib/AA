package com.serafinebot.p5.model;

/**
 * User-selected game configuration, using 1-based player numbers for the UI.
 *
 * <p>The controller validates and converts this display-facing data into a
 * {@link GameConfig}. The random keypad seed makes random layouts stable across
 * automatic recalculations until the user requests a new random layout.
 */
public record GameInput(
        int width,
        int height,
        int initialTotal,
        int limit,
        int lastPlayed,
        int playerCount,
        int startingPlayer,
        KeypadMode keypadMode,
        long keypadSeed,
        SolverMode solverMode
) {
    public GameInput(int width, int height, int initialTotal, int limit, int lastPlayed, int playerCount, int startingPlayer) {
        this(width, height, initialTotal, limit, lastPlayed, playerCount, startingPlayer, KeypadMode.STANDARD, 0L, SolverMode.TOP_DOWN_DP);
    }

    public GameInput(int width, int height, int initialTotal, int limit, int lastPlayed, int playerCount,
                     int startingPlayer, KeypadMode keypadMode, long keypadSeed) {
        this(width, height, initialTotal, limit, lastPlayed, playerCount, startingPlayer, keypadMode, keypadSeed, SolverMode.TOP_DOWN_DP);
    }

    public GameInput {
        if (keypadMode == null) throw new IllegalArgumentException("keypadMode must not be null");
        if (solverMode == null) throw new IllegalArgumentException("solverMode must not be null");
    }
}
