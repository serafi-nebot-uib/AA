package com.serafinebot.p6.model.agents;

import com.serafinebot.p6.model.Board;
import com.serafinebot.p6.model.Cell;
import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.Move;
import com.serafinebot.p6.model.Player;

import java.util.Comparator;
import java.util.List;

/**
 * One-ply heuristic agent.
 *
 * <p>The greedy agent evaluates every legal move by applying it once and scoring
 * only the resulting position. It does not consider the opponent's answer. This
 * makes it much faster than minimax, but also tactically short-sighted: it can
 * take an apparently good move that allows an immediate response by the other
 * player.</p>
 *
 * <p>The score has three simple cases:</p>
 * <ul>
 *     <li>Immediate own win: very high score.</li>
 *     <li>Immediate opponent win caused by the move: very low score.</li>
 *     <li>Otherwise: material advantage plus control of the central column.</li>
 * </ul>
 */
public final class GreedyAgent implements Agent {

    @Override
    public String name() {
        return "Greedy";
    }

    @Override
    public Move chooseMove(GameState state) {
        List<Move> moves = state.legalMoves();
        if (moves.isEmpty()) {
            throw new IllegalStateException("No legal moves available");
        }

        Player player = state.currentPlayer();
        return moves.stream()
                .max(Comparator.comparingInt(move -> score(state.apply(move), player)))
                .orElseThrow();
    }

    private int score(GameState state, Player player) {
        // Terminal states dominate every positional heuristic.
        if (state.result().isFinished()) {
            if (state.result().winner() == player) {
                return 100_000;
            }
            if (state.result().winner() == player.opponent()) {
                return -100_000;
            }
            return 0;
        }

        return materialScore(state.board(), player) + centerScore(state.board(), player);
    }

    private int materialScore(Board board, Player player) {
        // Removing opponent material is impossible directly, but rotations and
        // removals can change whether material remains useful. This simple term
        // still gives the greedy agent a stable positional preference.
        int score = 0;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                Cell cell = board.cellAt(row, column);
                if (cell == Cell.of(player)) {
                    score += 3;
                } else if (cell == Cell.of(player.opponent())) {
                    score -= 3;
                }
            }
        }
        return score;
    }

    private int centerScore(Board board, Player player) {
        // As in classic Connect 4, center control tends to participate in more
        // possible horizontal and diagonal lines than edge columns.
        int score = 0;
        int center = Board.SIZE / 2;
        for (int row = 0; row < Board.SIZE; row++) {
            Cell cell = board.cellAt(row, center);
            if (cell == Cell.of(player)) {
                score += 2;
            } else if (cell == Cell.of(player.opponent())) {
                score -= 2;
            }
        }
        return score;
    }
}
