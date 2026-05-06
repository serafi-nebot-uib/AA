package com.serafinebot.p5.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Solves the calculator game via memoized minimax over {@link GameState}
 * {@code (total, lastPlayed, turn)}.
 *
 * <p>Each player is assumed to play optimally to avoid being the loser. From a
 * given state the player about to move loses iff every legal move either
 * (a) reaches or exceeds the limit, immediately losing, or (b) leaves a
 * successor state in which the same player still ends up losing. The first
 * legal move (in ascending order) that hands the loss to a different player is
 * taken; this gives a deterministic tiebreak when several saving moves exist.
 *
 * <p>For 2 players the recursion collapses to standard minimax. For
 * {@code N > 2} it follows the cyclical "play not to lose" model: each player
 * is indifferent among moves that don't make them the loser.
 *
 * <p>Total strictly increases with every move, so the recursion is acyclic and
 * the memo grows monotonically. The cache is retained for the lifetime of the
 * solver, enabling O(1) follow-up queries from arbitrary states.
 */
public final class MinimaxSolver {

    private final GameConfig config;
    private final Map<GameState, Integer> memo = new HashMap<>();

    public MinimaxSolver(GameConfig config) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        this.config = config;
    }

    /** Loser of the game starting from {@link GameConfig#initialState()}. */
    public int losingPlayer() {
        return loser(config.initialState());
    }

    /**
     * Loser of the game starting from an arbitrary state, under optimal play.
     * Result is cached.
     */
    public int loser(GameState state) {
        Integer cached = memo.get(state);
        if (cached != null) return cached;

        int currentPlayer = state.turn();
        int chosenLoser = currentPlayer;

        for (int move : config.keypad().moves(state.lastPlayed())) {
            int nextTotal = state.total() + move;
            if (nextTotal >= config.limit()) continue;
            int nextTurn = (currentPlayer + 1) % config.playerCount();
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

    public GameConfig config() {
        return config;
    }

    /** Read-only view of the memo table populated so far. */
    public Map<GameState, Integer> memo() {
        return Collections.unmodifiableMap(memo);
    }
}
