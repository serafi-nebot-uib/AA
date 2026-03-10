package com.serafinebot.p2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Hamiltonian path solution.
 * Stores the sequence of positions visited and which piece made each move.
 */
public class HamiltonianPath {
    
    private final List<Position> positions;
    private final List<Integer> pieceIndices;  // 0 for piece1, 1 for piece2
    
    /**
     * Create an empty Hamiltonian path.
     */
    public HamiltonianPath() {
        this.positions = new ArrayList<>();
        this.pieceIndices = new ArrayList<>();
    }
    
    /**
     * Add a move to the path.
     * @param position The position to add
     * @param pieceIndex Index of the piece that made the move (0 or 1)
     */
    public void addMove(Position position, int pieceIndex) {
        positions.add(position);
        pieceIndices.add(pieceIndex);
    }
    
    /**
     * Remove the last move from the path (for backtracking).
     */
    public void removeLastMove() {
        if (!positions.isEmpty()) {
            positions.remove(positions.size() - 1);
            pieceIndices.remove(pieceIndices.size() - 1);
        }
    }
    
    /**
     * Get position at a specific step.
     * @param step Step number (0-based)
     * @return Position at that step
     */
    public Position getPosition(int step) {
        if (step < 0 || step >= positions.size()) {
            throw new IndexOutOfBoundsException("Invalid step: " + step);
        }
        return positions.get(step);
    }
    
    /**
     * Get which piece made the move at a specific step.
     * @param step Step number (0-based)
     * @return Piece index (0 or 1)
     */
    public int getPieceAtStep(int step) {
        if (step < 0 || step >= pieceIndices.size()) {
            throw new IndexOutOfBoundsException("Invalid step: " + step);
        }
        return pieceIndices.get(step);
    }
    
    /**
     * Get the length of the path.
     * @return Number of positions in the path
     */
    public int getLength() {
        return positions.size();
    }
    
    /**
     * Check if the path is empty.
     * @return true if no positions have been added
     */
    public boolean isEmpty() {
        return positions.isEmpty();
    }
    
    /**
     * Clear all positions and piece indices.
     */
    public void clear() {
        positions.clear();
        pieceIndices.clear();
    }
    
    /**
     * Get a copy of all positions in the path.
     * @return List of positions
     */
    public List<Position> getPositions() {
        return new ArrayList<>(positions);
    }
    
    /**
     * Get a copy of all piece indices.
     * @return List of piece indices
     */
    public List<Integer> getPieceIndices() {
        return new ArrayList<>(pieceIndices);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("HamiltonianPath[");
        sb.append("length=").append(getLength());
        if (!isEmpty()) {
            sb.append(", path=");
            for (int i = 0; i < positions.size(); i++) {
                if (i > 0) sb.append(" -> ");
                sb.append(positions.get(i)).append("(P").append(pieceIndices.get(i)).append(")");
                if (i >= 5 && positions.size() > 7) {
                    sb.append(" ... ");
                    int last = positions.size() - 1;
                    sb.append(positions.get(last)).append("(P").append(pieceIndices.get(last)).append(")");
                    break;
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
