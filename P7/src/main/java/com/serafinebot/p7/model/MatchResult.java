package com.serafinebot.p7.model;

/**
 * Result of one simulated game, including multiplayer winner information.
 *
 * <p>{@code winner} is zero-based. For the mandatory one-player game it is always
 * {@code 0}; for multiplayer experiments it identifies the player who first
 * reaches square 63. {@code turns} is the total number of counted turns consumed
 * by all players until the game ends.</p>
 */
public record MatchResult(int turns, int winner) {

    public MatchResult {
        if (turns <= 0) {
            throw new IllegalArgumentException("Turns must be positive.");
        }
        if (winner < 0) {
            throw new IllegalArgumentException("Winner index cannot be negative.");
        }
    }
}
