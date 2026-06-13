package com.serafinebot.p7.model;

/**
 * Result of applying one die roll and at most one special-square effect.
 *
 * <p>{@code landedPosition} is the square reached immediately after normal
 * movement and rebound. {@code position} is the square occupied after the single
 * allowed special effect, if any. Keeping both values lets the simulator count
 * visits to both the physical landing square and the teleport/effect destination
 * without applying a second effect in the same roll.</p>
 */
record MoveResult(int landedPosition, int position, int penaltyTurns, boolean extraRoll, boolean finished) {

    MoveResult {
        if (landedPosition < Game.START || landedPosition > Game.FINISH) {
            throw new IllegalArgumentException("Landed position must be between 0 and 63.");
        }
        if (position < Game.START || position > Game.FINISH) {
            throw new IllegalArgumentException("Position must be between 0 and 63.");
        }
        if (penaltyTurns < 0) {
            throw new IllegalArgumentException("Penalty turns cannot be negative.");
        }
        if (extraRoll && finished) {
            throw new IllegalArgumentException("A finished move cannot request another roll.");
        }
        if (finished && position != Game.FINISH) {
            throw new IllegalArgumentException("Only square 63 finishes the game.");
        }
    }
}
