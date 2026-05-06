package com.serafinebot.p5.model;

/** User-selected game configuration, using 1-based player numbers for the UI. */
public record GameInput(
        int width,
        int height,
        int initialTotal,
        int limit,
        int lastPlayed,
        int playerCount,
        int startingPlayer,
        KeypadMode keypadMode,
        long keypadSeed
) {
    public GameInput(int width, int height, int initialTotal, int limit, int lastPlayed, int playerCount, int startingPlayer) {
        this(width, height, initialTotal, limit, lastPlayed, playerCount, startingPlayer, KeypadMode.STANDARD, 0L);
    }

    public GameInput {
        if (keypadMode == null) throw new IllegalArgumentException("keypadMode must not be null");
    }
}
