package com.serafinebot.p2.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a chess board for the Hamiltonian path problem.
 * Manages board state, visited cells, and move generation.
 */
public class Board {
    
    private final int rows;
    private final int cols;
    private final boolean[][] visited;
    private int visitedCount;
    
    /**
     * Create a new board with the specified dimensions.
     * @param rows Number of rows (must be >= 4)
     * @param cols Number of columns (must be >= 4)
     */
    public Board(int rows, int cols) {
        if (rows < 4 || cols < 4) {
            throw new IllegalArgumentException("Board dimensions must be at least 4x4");
        }
        if (rows > 12 || cols > 12) {
            throw new IllegalArgumentException("Board dimensions must be at most 12x12");
        }
        
        this.rows = rows;
        this.cols = cols;
        this.visited = new boolean[rows][cols];
        this.visitedCount = 0;
    }
    
    /**
     * Check if a position is within board bounds.
     * @param pos Position to check
     * @return true if position is valid
     */
    public boolean isValidPosition(Position pos) {
        return pos.x() >= 0 && pos.x() < rows &&
               pos.y() >= 0 && pos.y() < cols;
    }
    
    /**
     * Check if a position has been visited.
     * @param pos Position to check
     * @return true if position is visited
     */
    public boolean isVisited(Position pos) {
        if (!isValidPosition(pos)) return false;
        return visited[pos.x()][pos.y()];
    }
    
    /**
     * Set a position as visited.
     * @param pos Position to mark
     */
    public void setVisited(Position pos) {
        if (!isValidPosition(pos)) {
            throw new IllegalArgumentException("Invalid position: " + pos);
        }
        if (!visited[pos.x()][pos.y()]) {
            visited[pos.x()][pos.y()] = true;
            visitedCount++;
        }
    }
    
    /**
     * Unset a position (for backtracking).
     * @param pos Position to unmark
     */
    public void unsetVisited(Position pos) {
        if (!isValidPosition(pos)) {
            throw new IllegalArgumentException("Invalid position: " + pos);
        }
        if (visited[pos.x()][pos.y()]) {
            visited[pos.x()][pos.y()] = false;
            visitedCount--;
        }
    }
    
    /**
     * Get all valid moves from a position for a given piece.
     * Excludes: out of bounds, already visited.
     * 
     * @param from The starting position
     * @param piece The piece type to move
     * @return List of valid destination positions
     */
    public List<Position> getValidMoves(Position from, PieceType piece) {
        List<Position> validMoves = new ArrayList<>();
        int[][] movements = piece.getMovements();
        
        if (piece.getMovementType() == MovementType.STATIC) {
            for (int[] move : movements) {
                try {
                    Position newPos = new Position(from.x() + move[0],
                                                   from.y() + move[1]);
                    if (isValidPosition(newPos) && !isVisited(newPos)) {
                        validMoves.add(newPos);
                    }
                } catch (IllegalArgumentException e) {
                    // Position with negative coordinates, skip
                }
            }
        } else {
            for (int[] direction : movements) {
                int steps = 1;
                while (true) {
                    try {
                        Position newPos = new Position(
                            from.x() + direction[0] * steps,
                            from.y() + direction[1] * steps
                        );
                        
                        if (!isValidPosition(newPos)) break;
                        if (isVisited(newPos)) break;
                        
                        validMoves.add(newPos);
                        steps++;
                    } catch (IllegalArgumentException e) {
                        // Position with negative coordinates, stop this direction
                        break;
                    }
                }
            }
        }
        
        return validMoves;
    }
    
    /**
     * Get valid moves sorted by Warnsdorff's heuristic.
     * Moves with fewer onward possibilities are prioritized.
     * 
     * @param from Starting position
     * @param piece Piece type
     * @return List of valid moves sorted by heuristic
     */
    public List<Position> getValidMovesSorted(Position from, PieceType piece) {
        List<Position> moves = getValidMoves(from, piece);
        
        // Sort by Warnsdorff's heuristic: fewer exits = higher priority
        moves.sort(Comparator.comparingInt(pos -> getValidMoves(pos, piece).size()));
        
        return moves;
    }
    
    /**
     * Check if piece1 at pos1 can capture piece2 at pos2.
     * Returns true if pos2 is in the attack range of piece1.
     * 
     * @param pos1 Position of first piece
     * @param piece1 Type of first piece
     * @param pos2 Position of second piece
     * @param piece2 Type of second piece (not used, but kept for extensibility)
     * @return true if piece1 can capture piece2
     */
    public boolean canCapture(Position pos1, PieceType piece1, 
                              Position pos2, PieceType piece2) {
        if (!isValidPosition(pos1) || !isValidPosition(pos2)) return false;
        if (pos1.equals(pos2)) return true;  // Same position
        
        int[][] movements = piece1.getMovements();
        
        if (piece1.getMovementType() == MovementType.STATIC) {
            // Check if pos2 is reachable in one move
            for (int[] move : movements) {
                try {
                    Position target = new Position(pos1.x() + move[0],
                                                  pos1.y() + move[1]);
                    if (target.equals(pos2)) return true;
                } catch (IllegalArgumentException e) {
                    // Position with negative coordinates, skip
                }
            }
        } else {
            // Check if pos2 is in any continuous line (ignoring obstacles)
            for (int[] direction : movements) {
                int steps = 1;
                while (true) {
                    try {
                        Position target = new Position(
                            pos1.x() + direction[0] * steps,
                            pos1.y() + direction[1] * steps
                        );
                        
                        if (!isValidPosition(target)) break;
                        if (target.equals(pos2)) return true;
                        
                        steps++;
                    } catch (IllegalArgumentException e) {
                        // Position with negative coordinates, stop this direction
                        break;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Reset the board to initial state (all cells unvisited).
     */
    public void reset() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                visited[r][c] = false;
            }
        }
        visitedCount = 0;
    }

    // Getters
    
    public int getRows() {
        return rows;
    }
    
    public int getCols() {
        return cols;
    }
    
    public int getTotalCells() {
        return rows * cols;
    }
    
    public int getVisitedCount() {
        return visitedCount;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Board[").append(rows).append("x").append(cols).append(", visited=").append(visitedCount).append("]\n");
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                sb.append(visited[r][c] ? "X " : ". ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
