package com.serafinebot.p5.model;

/**
 * Immutable snapshot of an in-progress calculator-game position.
 * Used as the state key for solver tables and memoization.
 *
 * @param total      current value on the calculator
 * @param lastPlayed previously played number, or 0 if no prior play
 * @param turn       index of the player about to move (0..playerCount-1)
 */
public record GameState(int total, int lastPlayed, int turn) {}
