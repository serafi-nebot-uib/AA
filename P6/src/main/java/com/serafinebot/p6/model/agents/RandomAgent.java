package com.serafinebot.p6.model.agents;

import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.Move;

import java.util.List;
import java.util.Random;

/**
 * Baseline agent that chooses uniformly among all legal moves.
 *
 * <p>This agent has no strategic knowledge: insertions, removals and rotations
 * have the same probability once they appear in {@link GameState#legalMoves()}.
 * It is useful as a weak opponent and as a reference point in the report: any
 * heuristic or minimax agent should outperform it over many games.</p>
 */
public final class RandomAgent implements Agent {

    private final Random random;

    public RandomAgent() {
        this(new Random());
    }

    /**
     * Constructor with injectable randomness, useful for deterministic tests or
     * reproducible benchmarks.
     */
    public RandomAgent(Random random) {
        this.random = random;
    }

    @Override
    public String name() {
        return "Random";
    }

    @Override
    public Move chooseMove(GameState state) {
        List<Move> moves = state.legalMoves();
        if (moves.isEmpty()) {
            throw new IllegalStateException("No legal moves available");
        }
        return moves.get(random.nextInt(moves.size()));
    }
}
