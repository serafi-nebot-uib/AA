package com.serafinebot.p6.model.agents;

import com.serafinebot.p6.model.Board;
import com.serafinebot.p6.model.Cell;
import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.Move;
import com.serafinebot.p6.model.Player;

import java.util.Comparator;
import java.util.List;

/**
 * Depth-limited minimax agent with alpha-beta pruning.
 *
 * <p>This is the strongest current agent. For each candidate move, it explores
 * an alternating game tree: the current player tries to maximize the heuristic
 * value and the opponent tries to minimize it. Because the full game tree is too
 * large for a 7x7 board with insertions, removals and rotations, the search is
 * limited to a configurable depth and non-terminal leaves are evaluated with a
 * heuristic.</p>
 *
 * <p>Alpha-beta pruning is the branch-and-bound component: {@code alpha} is the
 * best value already guaranteed for the maximizing player, and {@code beta} is
 * the best value already guaranteed for the minimizing player. Once
 * {@code alpha >= beta}, the remaining sibling moves cannot improve the final
 * decision and are discarded.</p>
 *
 * <p>The heuristic combines:</p>
 * <ul>
 *     <li>Terminal wins/losses with dominant scores.</li>
 *     <li>Potential windows of four cells: open triples, pairs and singles.</li>
 *     <li>Center-column control.</li>
 *     <li>Small material balance.</li>
 * </ul>
 */
public final class MinimaxAgent implements Agent {

    private static final int WIN_SCORE = 1_000_000;
    private static final int INF = 2_000_000;

    private final int maxDepth;
    private long lastNodes;
    private long lastPrunes;
    private long lastTimeMs;

    public MinimaxAgent() {
        this(3);
    }

    /**
     * @param maxDepth number of plies to explore; one ply is one move by one player
     */
    public MinimaxAgent(int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Depth must be positive");
        }
        this.maxDepth = maxDepth;
    }

    @Override
    public String name() {
        return "Minimax d=" + maxDepth;
    }

    @Override
    public Move chooseMove(GameState state) {
        // Move ordering is not required for correctness, but it makes alpha-beta
        // much more effective because strong moves are tested earlier.
        List<Move> moves = orderedMoves(state, state.currentPlayer());
        if (moves.isEmpty()) {
            throw new IllegalStateException("No legal moves available");
        }

        long start = System.nanoTime();
        lastNodes = 0;
        lastPrunes = 0;
        Player maximizer = state.currentPlayer();
        Move bestMove = moves.get(0);
        int bestScore = -INF;
        int alpha = -INF;

        for (Move move : moves) {
            int score = minimax(state.apply(move), maxDepth - 1, alpha, INF, maximizer);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestScore);
        }

        lastTimeMs = (System.nanoTime() - start) / 1_000_000;
        return bestMove;
    }

    public long lastNodes() {
        return lastNodes;
    }

    public long lastPrunes() {
        return lastPrunes;
    }

    public long lastTimeMs() {
        return lastTimeMs;
    }

    private int minimax(GameState state, int depth, int alpha, int beta, Player maximizer) {
        lastNodes++;
        if (depth == 0 || state.result().isFinished()) {
            return evaluate(state, maximizer, depth);
        }

        List<Move> moves = orderedMoves(state, maximizer);
        if (state.currentPlayer() == maximizer) {
            int value = -INF;
            for (Move move : moves) {
                value = Math.max(value, minimax(state.apply(move), depth - 1, alpha, beta, maximizer));
                alpha = Math.max(alpha, value);
                if (alpha >= beta) {
                    lastPrunes++;
                    break;
                }
            }
            return value;
        }

        int value = INF;
        for (Move move : moves) {
            value = Math.min(value, minimax(state.apply(move), depth - 1, alpha, beta, maximizer));
            beta = Math.min(beta, value);
            if (alpha >= beta) {
                lastPrunes++;
                break;
            }
        }
        return value;
    }

    private List<Move> orderedMoves(GameState state, Player maximizer) {
        return state.legalMoves().stream()
                .sorted(Comparator.comparingInt((Move move) -> quickScore(state, move, maximizer)).reversed())
                .toList();
    }

    private int quickScore(GameState state, Move move, Player maximizer) {
        // This light-weight ordering score is deliberately cheaper than the full
        // evaluation. It prioritizes immediate wins and central drops so pruning
        // can cut more branches.
        GameState next = state.apply(move);
        if (next.result().winner() == state.currentPlayer()) {
            return 100_000;
        }
        if (next.result().winner() == state.currentPlayer().opponent()) {
            return -100_000;
        }

        int score = switch (move.type()) {
            case DROP -> 50 - Math.abs(Board.SIZE / 2 - move.column()) * 6;
            case REMOVE -> 10;
            case ROTATE_LEFT, ROTATE_RIGHT -> 0;
        };
        return state.currentPlayer() == maximizer ? score : -score;
    }

    private int evaluate(GameState state, Player maximizer, int depth) {
        // Prefer faster wins and slower losses by adding the remaining depth to
        // terminal scores. A win found earlier in the tree has a larger value.
        if (state.result().winner() == maximizer) {
            return WIN_SCORE + depth;
        }
        if (state.result().winner() == maximizer.opponent()) {
            return -WIN_SCORE - depth;
        }
        if (state.result().isFinished()) {
            return 0;
        }

        Board board = state.board();
        return centerScore(board, maximizer) + windowScore(board, maximizer) + materialScore(board, maximizer);
    }

    private int centerScore(Board board, Player player) {
        int score = 0;
        int center = Board.SIZE / 2;
        for (int row = 0; row < Board.SIZE; row++) {
            score += cellScore(board.cellAt(row, center), player) * 6;
        }
        return score;
    }

    private int materialScore(Board board, Player player) {
        // Counts the raw balance of pieces on the board from the point of view
        // of 'player': own pieces add +1 and opponent pieces add -1.
        //
        // This term is intentionally small compared with windowScore. In this
        // game, having more pieces is not automatically winning because a player
        // may remove their own pieces and rotations can radically change the
        // position. Still, material is a useful tie-breaker when two positions
        // have similar tactical threats: preserving own pieces usually keeps more
        // future opportunities available.
        int score = 0;
        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                score += cellScore(board.cellAt(row, column), player);
            }
        }
        return score;
    }

    private int windowScore(Board board, Player player) {
        // Every group of four cells in the four winning directions is scored.
        // Blocked windows containing both colors are neutral because neither
        // player can complete that exact line anymore.
        //
        // This is the main positional heuristic. A "window" is any contiguous
        // segment of four cells horizontally, vertically or diagonally. Since the
        // objective is to create exactly such a segment, evaluating all possible
        // windows estimates how close each player is to winning even when no
        // terminal state has been reached at the current search depth.
        int score = 0;
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                for (int[] direction : directions) {
                    if (isWindowInside(row, column, direction[0], direction[1])) {
                        score += scoreWindow(board, row, column, direction[0], direction[1], player);
                    }
                }
            }
        }
        return score;
    }

    private int scoreWindow(Board board, int row, int column, int rowStep, int columnStep, Player player) {
        // Counts how many cells in this four-cell window belong to the evaluated
        // player and how many belong to the opponent. Empty cells are implicit:
        // CONNECT - own - opponent.
        int own = 0;
        int opponent = 0;
        for (int offset = 0; offset < Board.CONNECT; offset++) {
            Cell cell = board.cellAt(row + rowStep * offset, column + columnStep * offset);
            if (cell == Cell.of(player)) {
                own++;
            } else if (cell == Cell.of(player.opponent())) {
                opponent++;
            }
        }

        if (own > 0 && opponent > 0) {
            return 0;
        }
        // Four in a row should normally already be detected as a terminal state,
        // but keeping these values here makes the heuristic robust if it is ever
        // reused directly on a non-evaluated board.
        if (own == 4) {
            return 100_000;
        }
        if (opponent == 4) {
            return -100_000;
        }
        if (own == 3) {
            return 900;
        }
        if (opponent == 3) {
            // Opponent threats are penalized slightly more than own triples are
            // rewarded. This makes Minimax prefer blocking an immediate danger
            // over creating a symmetric-looking but slower threat.
            return -1_100;
        }
        if (own == 2) {
            return 90;
        }
        if (opponent == 2) {
            return -100;
        }
        if (own == 1) {
            return 8;
        }
        if (opponent == 1) {
            return -8;
        }
        return 0;
    }

    private int cellScore(Cell cell, Player player) {
        if (cell == Cell.of(player)) {
            return 1;
        }
        if (cell == Cell.of(player.opponent())) {
            return -1;
        }
        return 0;
    }

    private boolean isWindowInside(int row, int column, int rowStep, int columnStep) {
        int endRow = row + rowStep * (Board.CONNECT - 1);
        int endColumn = column + columnStep * (Board.CONNECT - 1);
        return endRow >= 0 && endRow < Board.SIZE && endColumn >= 0 && endColumn < Board.SIZE;
    }
}
