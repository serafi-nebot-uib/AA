package com.serafinebot.p2.model;

import java.util.List;
import java.util.function.Consumer;

/**
 * Solves the Hamiltonian path problem using recursive backtracking.
 * Two pieces alternate turns, with the constraint that they cannot capture each other.
 */
public class BacktrackingSolver {
    
    private final Board board;
    private final PieceType piece1Type;
    private final PieceType piece2Type;
    private final Position piece1Start;
    private final Position piece2Start;
    
    private final HamiltonianPath solution;
    private final SolverMetrics metrics;
    
    private volatile boolean stopped;
    private Consumer<Integer> progressCallback;
    
    private static final int PROGRESS_UPDATE_INTERVAL = 1000;  // Update every 1000 iterations
    
    /**
     * Create a new solver instance.
     * 
     * @param board The board to solve on
     * @param piece1Type Type of first piece
     * @param piece1Start Starting position of first piece
     * @param piece2Type Type of second piece
     * @param piece2Start Starting position of second piece
     */
    public BacktrackingSolver(Board board, 
                             PieceType piece1Type, Position piece1Start,
                             PieceType piece2Type, Position piece2Start) {
        if (piece1Start.equals(piece2Start)) {
            throw new IllegalArgumentException("Pieces cannot start at the same position");
        }
        
        this.board = board;
        this.piece1Type = piece1Type;
        this.piece2Type = piece2Type;
        this.piece1Start = piece1Start;
        this.piece2Start = piece2Start;
        
        this.solution = new HamiltonianPath();
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
        board.setVisited(piece1Start);
        board.setVisited(piece2Start);
        solution.addMove(piece1Start, 0);
        solution.addMove(piece2Start, 1);
        
        // Check if pieces can capture each other at start
        if (board.canCapture(piece1Start, piece1Type, piece2Start, piece2Type) ||
            board.canCapture(piece2Start, piece2Type, piece1Start, piece1Type)) {
            metrics.stopTimer();
            metrics.setSolutionFound(false);
            return false;
        }
        
        // Start backtracking from piece 1's turn (since piece 2 just moved)
        boolean found = backtrack(piece1Start, piece1Type, 0, 
                                 piece2Start, piece2Type, 1, 
                                 2);  // depth = 2 (both pieces placed)
        
        // Stop timer
        metrics.stopTimer();
        metrics.setSolutionFound(found);
        
        return found;
    }
    
    /**
     * Recursive backtracking algorithm.
     * 
     * @param currentPos Current piece's position
     * @param currentPiece Current piece's type
     * @param currentIndex Current piece's index (0 or 1)
     * @param otherPos Other piece's position
     * @param otherPiece Other piece's type
     * @param otherIndex Other piece's index (1 or 0)
     * @param depth Current recursion depth
     * @return true if solution found, false otherwise
     */
    private boolean backtrack(Position currentPos, PieceType currentPiece, int currentIndex,
                             Position otherPos, PieceType otherPiece, int otherIndex,
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
            if (board.canCapture(nextPos, currentPiece, otherPos, otherPiece)) {
                continue;  // Skip this move
            }
            
            // Validation 2: Check if other piece can capture current piece at nextPos
            if (board.canCapture(otherPos, otherPiece, nextPos, currentPiece)) {
                continue;  // Skip this move
            }
            
            // Apply move
            board.setVisited(nextPos);
            solution.addMove(nextPos, currentIndex);
            
            // Recurse with swapped pieces (alternate turn)
            boolean success = backtrack(otherPos, otherPiece, otherIndex,
                                       nextPos, currentPiece, currentIndex,
                                       depth + 1);
            
            if (success) {
                return true;  // Solution found!
            }
            
            // Backtrack
            metrics.incrementBacktracks();
            board.unsetVisited(nextPos);
            solution.removeLastMove();
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
    
    public HamiltonianPath getSolution() {
        return solution;
    }
    
    public SolverMetrics getMetrics() {
        return metrics;
    }
    
    public Board getBoard() {
        return board;
    }
    
    public PieceType getPiece1Type() {
        return piece1Type;
    }
    
    public PieceType getPiece2Type() {
        return piece2Type;
    }
    
    public Position getPiece1Start() {
        return piece1Start;
    }
    
    public Position getPiece2Start() {
        return piece2Start;
    }
}
