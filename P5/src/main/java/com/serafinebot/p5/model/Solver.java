package com.serafinebot.p5.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared contract and replay logic for calculator-game solvers.
 *
 * <p>The solved state is {@code (total, lastPlayed, turn)}. The value stored by
 * every solver is the index of the player who will lose from that state when all
 * players choose optimally to avoid being the loser. A move that reaches or
 * exceeds the configured limit is terminal and makes the mover lose immediately.
 *
 * <p>Both DP implementations use the same deterministic tie break: moves are
 * examined in ascending numeric order and the first move that makes somebody
 * other than the current player lose is selected. If no such safe move exists,
 * the current player is the computed loser and the first legal move is used for
 * replay, because all legal moves are losing for the current player.
 */
public abstract class Solver {

    private final GameConfig config;

    protected Solver(GameConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        this.config = config;
    }

    /** Loser of the game starting from {@link GameConfig#initialState()}. */
    public final int losingPlayer() {
        return loser(config.initialState());
    }

    /**
     * Returns the 0-based loser index for an arbitrary state under optimal play.
     * Implementations may compute the answer lazily or read it from a filled DP
     * table, but must follow the recurrence documented in this class.
     */
    public abstract int loser(GameState state);

    /**
     * Number of states computed or cached by the concrete solver.
     * Top-down DP reports the reached memo size; bottom-up DP reports the full
     * table size.
     */
    public abstract int stateCount();

    /**
     * Deterministic move used for replay under the same optimal-play policy.
     *
     * <p>The method is intentionally defined once so top-down and bottom-up
     * solvers produce the same replay whenever their {@link #loser(GameState)}
     * values agree.
     */
    public int chosenMove(GameState state) {
        int currentPlayer = state.turn();
        int fallback = -1;

        for (int move : config.keypad().moves(state.lastPlayed())) {
            if (fallback == -1) fallback = move;
            int nextTotal = state.total() + move;
            // Legal moves are sorted, so once one move is terminal every later move is too.
            if (nextTotal >= config.limit()) break;

            int nextTurn = (currentPlayer + 1) % config.playerCount();
            int childLoser = loser(new GameState(nextTotal, move, nextTurn));
            if (childLoser != currentPlayer) return move;
        }

        if (fallback == -1) throw new IllegalStateException("state has no legal moves: " + state);
        return fallback;
    }

    /** Builds the optimal-play line shown by the GUI replay controls. */
    public final List<ReplayStep> replay() {
        List<ReplayStep> replay = new ArrayList<>();
        GameState state = config.initialState();
        replay.add(new ReplayStep(
                0,
                state.total(),
                state.total(),
                state.lastPlayed(),
                0,
                0,
                state.turn() + 1,
                config.keypad().moves(state.lastPlayed()),
                false
        ));

        while (state.total() < config.limit()) {
            int move = chosenMove(state);
            int nextTotal = state.total() + move;
            boolean terminal = nextTotal >= config.limit();
            int nextTurn = (state.turn() + 1) % config.playerCount();
            replay.add(new ReplayStep(
                    replay.size(),
                    state.total(),
                    nextTotal,
                    move,
                    state.turn() + 1,
                    move,
                    terminal ? 0 : nextTurn + 1,
                    terminal ? new int[0] : config.keypad().moves(move),
                    terminal
            ));
            if (terminal) break;
            state = new GameState(nextTotal, move, nextTurn);
        }

        return replay;
    }

    /** Configuration shared by all computations performed by this solver. */
    public final GameConfig config() {
        return config;
    }
}
