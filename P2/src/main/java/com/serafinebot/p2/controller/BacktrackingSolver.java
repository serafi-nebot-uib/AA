package com.serafinebot.p2.controller;

import com.serafinebot.p2.model.Board;
import com.serafinebot.p2.model.PieceType;
import com.serafinebot.p2.model.Position;
import com.serafinebot.p2.model.SolverMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Solves the Hamiltonian path problem using recursive backtracking.
 * Two pieces alternate turns, with the constraint that they cannot capture each other.
 */
public class BacktrackingSolver {
    
    private final Board board;
    private final PieceType whiteType;
    private final PieceType blackType;
    private final Position whiteStart;
    private final Position blackStart;
    
    private final List<Position> solution;
    private final SolverMetrics metrics;
    
    private volatile boolean stopped;
    private Consumer<Integer> progressCallback;
    
    static final int PROGRESS_UPDATE_INTERVAL = 1000;  // Update every 1000 iterations
    
    /**
     * Create a new solver instance.
     * 
     * @param board The board to solve on
     * @param whiteType  Type of the white piece
     * @param whiteStart Starting position of the white piece
     * @param blackType  Type of the black piece
     * @param blackStart Starting position of the black piece
     */
    public BacktrackingSolver(Board board,
                             PieceType whiteType, Position whiteStart,
                             PieceType blackType, Position blackStart) {
        if (whiteStart.equals(blackStart)) {
            throw new IllegalArgumentException("Pieces cannot start at the same position");
        }

        this.board = board;
        this.whiteType  = whiteType;
        this.blackType  = blackType;
        this.whiteStart = whiteStart;
        this.blackStart = blackStart;
        
        this.solution = new ArrayList<>();
        this.metrics = new SolverMetrics();
        this.stopped = false;
    }
    
    /**
     * Set a callback to be notified of progress.
     * @param callback Consumer that receives the number of visited cells
     */
    public void setProgressCallback(Consumer<Integer> callback) {
        this.progressCallback = callback;
    }
    
    /**
     * Solve the Hamiltonian path problem.
     * @return true if a solution was found, false otherwise
     */
    public boolean solve() {
        // Reset state
        board.reset();
        solution.clear();
        metrics.reset();

        stopped = false;
        
        // Start timer
        metrics.startTimer();
        
        // Mark initial positions as visited
        board.setVisited(whiteStart);
        board.setVisited(blackStart);
        solution.add(whiteStart);
        solution.add(blackStart);

        // Check if pieces can capture each other at start
        if (board.canCapture(whiteStart, whiteType, blackStart) ||
            board.canCapture(blackStart, blackType, whiteStart)) {
            metrics.stopTimer();
            metrics.setSolutionFound(false);
            return false;
        }

        // Start exploring from white's turn (since black was just placed)
        boolean found = search(whiteStart, whiteType,
                                 blackStart, blackType,
                                 2);  // depth = 2 (both pieces placed)
        
        // Stop timer
        metrics.stopTimer();
        metrics.setSolutionFound(found);
        
        return found;
    }
    
    /**
     * Explores the search tree recursively (the backtracking occurs when this returns false).
     * Pieces alternate turns: currentPiece moves, then otherPiece, then currentPiece, etc.
     *
     * @param currentPos   Current piece's position
     * @param currentPiece Current piece's type
     * @param otherPos     Other piece's position
     * @param otherPiece   Other piece's type
     * @param depth        Current recursion depth
     * @return true if solution found, false otherwise
     */
    private boolean search(Position currentPos, PieceType currentPiece,
                             Position otherPos, PieceType otherPiece,
                             int depth) {
        // Base case: All cells visited
        if (board.getVisitedCount() == board.getTotalCells()) {
            return true;
        }
        
        // Stop check
        if (stopped) {
            return false;
        }
        
        // Update metrics
        metrics.incrementIterations();
        metrics.updateMaxDepth(depth);
        
        // Progress callback (every N iterations)
        if (progressCallback != null && metrics.getIterations() % PROGRESS_UPDATE_INTERVAL == 0) {
            progressCallback.accept(board.getVisitedCount());
        }
        
        // Get valid moves for current piece, sorted by Warnsdorff's heuristic
        List<Position> validMoves = board.getValidMovesSorted(currentPos, currentPiece);
        
        // Try each valid move
        for (Position nextPos : validMoves) {
            // Validation 1: Check if moving to nextPos would allow current piece to capture other piece
            if (board.canCapture(nextPos, currentPiece, otherPos)) {
                continue;  // Skip this move
            }

            // Validation 2: Check if other piece can capture current piece at nextPos
            if (board.canCapture(otherPos, otherPiece, nextPos)) {
                continue;  // Skip this move
            }
            
            // Apply move
            board.setVisited(nextPos);
            solution.add(nextPos);
            
            // Recurse with swapped pieces (alternate turn)
            boolean success = search(otherPos, otherPiece,
                                       nextPos, currentPiece,
                                       depth + 1);
            
            if (success) {
                return true;  // Solution found!
            }
            
            // Backtrack
            metrics.incrementBacktracks();
            board.unsetVisited(nextPos);
            solution.remove(solution.size() - 1);
        }
        
        // No valid moves found
        return false;
    }
    
    /**
     * Stop the solving process gracefully.
     */
    public void stop() {
        this.stopped = true;
    }
    
    /**
     * Check if the solver has been stopped.
     * @return true if stopped
     */
    public boolean isStopped() {
        return stopped;
    }
    
    // Getters
    
    public List<Position> getSolution() {
        return solution;
    }
    
    public SolverMetrics getMetrics() {
        return metrics;
    }
    
    public Board getBoard() {
        return board;
    }
    
    public PieceType getWhiteType() {
        return whiteType;
    }

    public PieceType getBlackType() {
        return blackType;
    }

    public Position getWhiteStart() {
        return whiteStart;
    }

    public Position getBlackStart() {
        return blackStart;
    }
}
