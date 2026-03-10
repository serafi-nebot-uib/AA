package com.serafinebot.p2;

import com.serafinebot.p2.model.*;

/**
 * Quick test to verify solver finds solutions.
 */
public class QuickTest {
    
    public static void main(String[] args) {
        System.out.println("=== Quick Solver Test ===\n");
        
        // Test with 4x4 board and different starting positions
        testConfiguration(4, 4, 
            PieceType.KNIGHT, new Position(0, 0),
            PieceType.KNIGHT, new Position(3, 3));
        
        testConfiguration(4, 4,
            PieceType.KNIGHT, new Position(1, 1),
            PieceType.KNIGHT, new Position(2, 2));
    }
    
    private static void testConfiguration(int rows, int cols,
                                          PieceType piece1, Position pos1,
                                          PieceType piece2, Position pos2) {
        System.out.printf("Testing %dx%d: %s at %s, %s at %s%n",
            rows, cols, piece1.getName(), pos1, piece2.getName(), pos2);
        
        Board board = new Board(rows, cols);
        BacktrackingSolver solver = new BacktrackingSolver(
            board, piece1, pos1, piece2, pos2
        );
        
        boolean found = solver.solve();
        
        System.out.printf("  Result: %s%n", found ? "FOUND!" : "No solution");
        System.out.printf("  Time: %.3fs, Iterations: %d, Backtracks: %d%n",
            solver.getMetrics().getElapsedTimeSeconds(),
            solver.getMetrics().getIterations(),
            solver.getMetrics().getBacktracks());
        
        if (found) {
            System.out.println("  Path length: " + solver.getSolution().getLength());
        }
        System.out.println();
    }
}
