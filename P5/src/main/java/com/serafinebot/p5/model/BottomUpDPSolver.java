package com.serafinebot.p5.model;

import java.util.Arrays;

/**
 * Bottom-up dynamic-programming solver for the calculator game.
 *
 * <p>The table dimensions are {@code total x lastPlayed x turn}. Totals are
 * filled from {@code limit - 1} down to {@code 0}; this order is valid because
 * every legal move strictly increases the total, so every safe successor has
 * already been computed when the current state is evaluated.
 *
 * <p>For a keypad with {@code K = width * height} keys, {@code P} players,
 * losing limit {@code L}, and at most {@code B} legal moves per state, the
 * running time is {@code O(L * (K + 1) * P * B)} and the memory usage is
 * {@code O(L * (K + 1) * P)}.
 */
public final class BottomUpDPSolver extends Solver {

    private final int[][][] losers;
    private final int[][][] moves;
    private final int stateCount;

    public BottomUpDPSolver(GameConfig config) {
        super(config);
        int keyCount = config.keypad().width() * config.keypad().height();
        this.losers = new int[config.limit()][keyCount + 1][config.playerCount()];
        this.moves = new int[config.limit()][keyCount + 1][config.playerCount()];
        this.stateCount = fillTables(keyCount);
    }

    @Override
    public int loser(GameState state) {
        validate(state);
        return losers[state.total()][state.lastPlayed()][state.turn()];
    }

    @Override
    public int chosenMove(GameState state) {
        validate(state);
        return moves[state.total()][state.lastPlayed()][state.turn()];
    }

    @Override
    public int stateCount() {
        return stateCount;
    }

    private int fillTables(int keyCount) {
        int count = 0;
        for (int[][] byLast : moves) {
            for (int[] byTurn : byLast) Arrays.fill(byTurn, -1);
        }

        // Descending totals make each safe successor available in the table.
        for (int total = config().limit() - 1; total >= 0; total--) {
            for (int lastPlayed = 0; lastPlayed <= keyCount; lastPlayed++) {
                int[] legalMoves = config().keypad().moves(lastPlayed);
                for (int turn = 0; turn < config().playerCount(); turn++) {
                    fillState(total, lastPlayed, turn, legalMoves);
                    count++;
                }
            }
        }
        return count;
    }

    private void fillState(int total, int lastPlayed, int turn, int[] legalMoves) {
        int chosenLoser = turn;
        int chosenMove = legalMoves[0];

        // Find the first safe move that transfers the loss to another player.
        for (int move : legalMoves) {
            int nextTotal = total + move;
            if (nextTotal >= config().limit()) continue;

            int nextTurn = (turn + 1) % config().playerCount();
            int childLoser = losers[nextTotal][move][nextTurn];
            if (childLoser != turn) {
                chosenLoser = childLoser;
                chosenMove = move;
                break;
            }
        }

        losers[total][lastPlayed][turn] = chosenLoser;
        moves[total][lastPlayed][turn] = chosenMove;
    }

    private void validate(GameState state) {
        if (state.total() < 0 || state.total() >= config().limit()) {
            throw new IllegalArgumentException("state total outside solved range: " + state.total());
        }
        if (state.lastPlayed() < 0 || state.lastPlayed() >= losers[state.total()].length) {
            throw new IllegalArgumentException("state lastPlayed outside keypad range: " + state.lastPlayed());
        }
        if (state.turn() < 0 || state.turn() >= config().playerCount()) {
            throw new IllegalArgumentException("state turn outside player range: " + state.turn());
        }
    }
}
