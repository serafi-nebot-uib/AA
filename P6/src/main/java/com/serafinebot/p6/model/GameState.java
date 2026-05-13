package com.serafinebot.p6.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete immutable game state: board, player to move and current result.
 *
 * <p>The state owns the rules that depend on turns and end conditions. The board
 * knows how pieces move physically, while this record decides which moves are
 * legal for the current player and when the game has ended.</p>
 */
public record GameState(Board board, Player currentPlayer, GameResult result) {

    public GameState() {
        this(new Board(), Player.RED, GameResult.inProgress());
    }

    public List<Move> legalMoves() {
        if (result.isFinished()) {
            return List.of();
        }

        List<Move> moves = new ArrayList<>();
        for (int column : board.legalDropColumns()) {
            moves.add(Move.drop(column));
        }

        // The statement forbids continuing by removing pieces once the board is
        // full. Therefore removals are generated only while there is still room.
        if (!board.isFull()) {
            moves.addAll(board.legalRemovals(currentPlayer));
        }

        moves.add(Move.rotateLeft());
        moves.add(Move.rotateRight());
        return moves;
    }

    public GameState apply(Move move) {
        if (result.isFinished()) {
            throw new IllegalStateException("Cannot move after game has finished");
        }
        if (!isLegal(move)) {
            throw new IllegalArgumentException("Illegal move: " + move);
        }

        Board nextBoard = switch (move.type()) {
            case DROP -> board.drop(move.column(), currentPlayer);
            case REMOVE -> board.remove(move.row(), move.column(), currentPlayer);
            case ROTATE_LEFT -> board.rotateLeft();
            case ROTATE_RIGHT -> board.rotateRight();
        };

        // Wins are evaluated after every move, including opponent removals and
        // rotations. The winner may therefore be the player who did not move.
        return new GameState(nextBoard, currentPlayer.opponent(), evaluate(nextBoard));
    }

    public boolean isLegal(Move move) {
        return switch (move.type()) {
            case DROP -> board.canDrop(move.column());
            case REMOVE -> !board.isFull() && board.canRemove(move.row(), move.column(), currentPlayer);
            case ROTATE_LEFT, ROTATE_RIGHT -> true;
        };
    }

    private static GameResult evaluate(Board board) {
        boolean redWins = board.hasConnectFour(Player.RED);
        boolean yellowWins = board.hasConnectFour(Player.YELLOW);

        // A removal or rotation can create four-in-a-row for both players at the
        // same time. The assignment defines this situation as a draw.
        if (redWins && yellowWins) {
            return GameResult.draw();
        }
        if (redWins) {
            return GameResult.win(Player.RED);
        }
        if (yellowWins) {
            return GameResult.win(Player.YELLOW);
        }
        if (board.isFull()) {
            return GameResult.draw();
        }
        return GameResult.inProgress();
    }
}
