package com.serafinebot.p5.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Solves the calculator game via top-down dynamic programming over
 * {@link GameState} {@code (total, lastPlayed, turn)}.
 *
 * <p>Each player is assumed to play optimally to avoid being the loser. From a
 * given state the player about to move loses iff every legal move either
 * (a) reaches or exceeds the limit, immediately losing, or (b) leaves a
 * successor state in which the same player still ends up losing. The first
 * legal move (in ascending order) that hands the loss to a different player is
 * taken; this gives a deterministic tiebreak when several saving moves exist.
 *
 * <p>The recurrence is evaluated lazily from the requested state and cached in
 * {@code memo}. This is dynamic programming in top-down form: only states
 * reachable from the queried position are expanded.
 *
 * <p>Total strictly increases with every move, so the recursion is acyclic and
 * the memo grows monotonically. The cache is retained for the lifetime of the
 * solver, enabling O(1) follow-up queries for already solved states.
 *
 * <p>If {@code R} states are reached and {@code B} is the maximum number of
 * legal moves in a state, the running time is {@code O(R * B)} and the memory
 * usage is {@code O(R)}.
 */
public final class TopDownDPSolver extends Solver {

    private final Map<GameState, Integer> memo = new HashMap<>();

    public TopDownDPSolver(GameConfig config) {
        super(config);
    }

    /**
     * Loser of the game starting from an arbitrary state, under optimal play.
     * Result is cached so every state is evaluated at most once.
     */
    @Override
    public int loser(GameState state) {
        Integer cached = memo.get(state);
        if (cached != null) return cached;

        int currentPlayer = state.turn();
        int chosenLoser = currentPlayer;

        for (int move : config().keypad().moves(state.lastPlayed())) {
            int nextTotal = state.total() + move;
            if (nextTotal >= config().limit()) continue;
            int nextTurn = (currentPlayer + 1) % config().playerCount();
            GameState next = new GameState(nextTotal, move, nextTurn);
            int childLoser = loser(next);
            if (childLoser != currentPlayer) {
                chosenLoser = childLoser;
                break;
            }
        }

        memo.put(state, chosenLoser);
        return chosenLoser;
    }

    @Override
    public int stateCount() {
        return memo.size();
    }

    /** Read-only view of the memo table populated so far. */
    public Map<GameState, Integer> memo() {
        return Collections.unmodifiableMap(memo);
    }
}
