package com.serafinebot.p5.model;

/**
 * Validated, immutable configuration for a single solver run.
 *
 * <p>This record is deliberately UI-independent: player indexes are 0-based and
 * the keypad layout is already materialized. {@link GameInput} is converted to
 * this model object by the controller before a solver is created.
 *
 * @param keypad         the calculator keypad layout
 * @param initialTotal   value on the calculator at the start of the solve, {@code >= 0}
 * @param limit          losing threshold; the player who reaches or exceeds this loses
 * @param lastPlayed     previously played number, or 0 if no prior play
 * @param playerCount    number of players, {@code >= 2}
 * @param startingPlayer index of the player about to move, in {@code [0, playerCount)}
 */
public record GameConfig(
        Keypad keypad,
        int initialTotal,
        int limit,
        int lastPlayed,
        int playerCount,
        int startingPlayer
) {
    public GameConfig {
        if (keypad == null) {
            throw new IllegalArgumentException("keypad must not be null");
        }
        if (initialTotal < 0) {
            throw new IllegalArgumentException("initialTotal must be >= 0: " + initialTotal);
        }
        if (limit <= initialTotal) {
            throw new IllegalArgumentException("limit (" + limit + ") must be > initialTotal (" + initialTotal + ")");
        }
        if (lastPlayed != 0 && !keypad.contains(lastPlayed)) {
            throw new IllegalArgumentException("lastPlayed must be 0 or a keypad value: " + lastPlayed);
        }
        if (playerCount < 2) {
            throw new IllegalArgumentException("playerCount must be >= 2: " + playerCount);
        }
        if (startingPlayer < 0 || startingPlayer >= playerCount) {
            throw new IllegalArgumentException(
                    "startingPlayer (" + startingPlayer + ") not in [0, " + playerCount + ")");
        }
    }

    /** State the solver begins from. */
    public GameState initialState() {
        return new GameState(initialTotal, lastPlayed, startingPlayer);
    }
}
