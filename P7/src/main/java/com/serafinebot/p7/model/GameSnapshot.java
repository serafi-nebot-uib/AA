package com.serafinebot.p7.model;

/**
 * Immutable state of the one-player Oca game between counted turns.
 *
 * <p>The state contains only the information needed to resume a game: the current
 * square and the number of penalty turns still pending. It intentionally does not
 * store statistics or random state; those responsibilities belong to the
 * simulator and the batch runner.</p>
 */
public record GameSnapshot(int position, int pendingPenaltyTurns) {

    public GameSnapshot {
        if (position < Game.START || position > Game.FINISH) {
            throw new IllegalArgumentException("Position must be between 0 and 63.");
        }
        if (pendingPenaltyTurns < 0) {
            throw new IllegalArgumentException("Pending penalties cannot be negative.");
        }
    }

    /**
     * Returns {@code true} when the player is on the final square and no more
     * turns should be simulated.
     */
    public boolean isFinished() {
        return position == Game.FINISH;
    }
}
