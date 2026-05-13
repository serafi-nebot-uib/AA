package com.serafinebot.p6.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable representation of the physical 7x7 board.
 *
 * <p>All public operations that change the board return a new {@code Board}
 * instance instead of modifying the current one. This is important for minimax:
 * the search can freely explore future states without corrupting the real game
 * shown in the UI.</p>
 *
 * <p>Rows grow from top to bottom and columns grow from left to right. Gravity
 * always pulls pieces toward the largest row index. After a removal or rotation,
 * gravity is re-applied column by column.</p>
 */
public final class Board {

    public static final int SIZE = 7;
    public static final int CONNECT = 4;

    private final Cell[][] cells;

    public Board() {
        cells = new Cell[SIZE][SIZE];
        for (Cell[] row : cells) {
            Arrays.fill(row, Cell.EMPTY);
        }
    }

    private Board(Cell[][] cells) {
        this.cells = cells;
    }

    public Cell cellAt(int row, int column) {
        checkPosition(row, column);
        return cells[row][column];
    }

    public boolean canDrop(int column) {
        checkColumn(column);
        return cells[0][column] == Cell.EMPTY;
    }

    public boolean canRemove(int row, int column, Player player) {
        checkPosition(row, column);
        return cells[row][column] == Cell.of(player);
    }

    public Board drop(int column, Player player) {
        if (!canDrop(column)) {
            throw new IllegalArgumentException("Column is full: " + column);
        }

        Board next = copy();
        for (int row = SIZE - 1; row >= 0; row--) {
            if (next.cells[row][column] == Cell.EMPTY) {
                next.cells[row][column] = Cell.of(player);
                return next;
            }
        }
        throw new IllegalStateException("No empty cell found after canDrop passed");
    }

    public Board remove(int row, int column, Player player) {
        if (!canRemove(row, column, player)) {
            throw new IllegalArgumentException("Can only remove own pieces");
        }

        Board next = copy();
        next.cells[row][column] = Cell.EMPTY;
        next.applyGravity();
        return next;
    }

    public Board rotateLeft() {
        // Matrix rotation first changes the orientation. Gravity is applied
        // afterwards because pieces fall according to the new board direction.
        Cell[][] rotated = emptyCells();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                rotated[SIZE - 1 - column][row] = cells[row][column];
            }
        }
        Board next = new Board(rotated);
        next.applyGravity();
        return next;
    }

    public Board rotateRight() {
        // Clockwise rotation: source (row, column) maps to
        // destination (column, SIZE - 1 - row).
        Cell[][] rotated = emptyCells();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                rotated[column][SIZE - 1 - row] = cells[row][column];
            }
        }
        Board next = new Board(rotated);
        next.applyGravity();
        return next;
    }

    public boolean isFull() {
        for (int column = 0; column < SIZE; column++) {
            if (cells[0][column] == Cell.EMPTY) {
                return false;
            }
        }
        return true;
    }

    public int pieceCount() {
        int count = 0;
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                if (cell != Cell.EMPTY) {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean hasConnectFour(Player player) {
        // Only four forward directions are needed. The opposite directions would
        // find the same lines twice.
        Cell target = Cell.of(player);
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (cells[row][column] != target) {
                    continue;
                }
                for (int[] direction : directions) {
                    if (hasLine(row, column, direction[0], direction[1], target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public List<Integer> legalDropColumns() {
        List<Integer> columns = new ArrayList<>();
        for (int column = 0; column < SIZE; column++) {
            if (canDrop(column)) {
                columns.add(column);
            }
        }
        return columns;
    }

    public List<Move> legalRemovals(Player player) {
        List<Move> moves = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (cells[row][column] == Cell.of(player)) {
                    moves.add(Move.remove(row, column));
                }
            }
        }
        return moves;
    }

    public Board copy() {
        Cell[][] copy = new Cell[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            System.arraycopy(cells[row], 0, copy[row], 0, SIZE);
        }
        return new Board(copy);
    }

    private void applyGravity() {
        // Compact each column from bottom to top. writeRow marks the next cell
        // where a non-empty piece should land.
        for (int column = 0; column < SIZE; column++) {
            int writeRow = SIZE - 1;
            for (int row = SIZE - 1; row >= 0; row--) {
                if (cells[row][column] != Cell.EMPTY) {
                    cells[writeRow][column] = cells[row][column];
                    if (writeRow != row) {
                        cells[row][column] = Cell.EMPTY;
                    }
                    writeRow--;
                }
            }
            while (writeRow >= 0) {
                cells[writeRow][column] = Cell.EMPTY;
                writeRow--;
            }
        }
    }

    private boolean hasLine(int row, int column, int rowStep, int columnStep, Cell target) {
        for (int offset = 1; offset < CONNECT; offset++) {
            int nextRow = row + rowStep * offset;
            int nextColumn = column + columnStep * offset;
            if (!isInside(nextRow, nextColumn) || cells[nextRow][nextColumn] != target) {
                return false;
            }
        }
        return true;
    }

    private static Cell[][] emptyCells() {
        Cell[][] result = new Cell[SIZE][SIZE];
        for (Cell[] row : result) {
            Arrays.fill(row, Cell.EMPTY);
        }
        return result;
    }

    private static boolean isInside(int row, int column) {
        return row >= 0 && row < SIZE && column >= 0 && column < SIZE;
    }

    private static void checkPosition(int row, int column) {
        if (!isInside(row, column)) {
            throw new IllegalArgumentException("Invalid position: " + row + ", " + column);
        }
    }

    private static void checkColumn(int column) {
        if (column < 0 || column >= SIZE) {
            throw new IllegalArgumentException("Invalid column: " + column);
        }
    }
}
