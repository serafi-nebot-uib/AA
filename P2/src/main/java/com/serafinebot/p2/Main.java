package com.serafinebot.p2;

import com.serafinebot.p2.model.*;

import java.util.List;

/**
 * Test program for the Hamiltonian path solver.
 * Demonstrates basic board functionality and path solving.
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("=== P2: Hamiltonian Path with Backtracking ===\n");
        
        // Test 1: PieceType enum
        testPieceTypes();
        
        // Test 2: Position record
        testPosition();
        
        // Test 3: Board functionality
        testBoard();
        
        // Test 4: Solve small board (4x4 with Knights)
        testSolverSmall();
        
        // Test 5: Solve 5x5 with different pieces
        testSolverMedium();
    }
    
    private static void testPieceTypes() {
        System.out.println("--- Test 1: PieceType Enum ---");
        System.out.println("Available pieces:");
        for (PieceType piece : PieceType.values()) {
            System.out.printf("  %s (%s) - Movement: %s, Complexity: %.1f%n",
                piece.getName(), piece.getShortName(), 
                piece.getMovementType(), piece.getComplexity());
        }
        System.out.println();
    }
    
    private static void testPosition() {
        System.out.println("--- Test 2: Position Record ---");
        Position p1 = new Position(0, 0);
        Position p2 = new Position(1, 1);
        Position p3 = new Position(0, 1);
        
        System.out.println("p1: " + p1);
        System.out.println("p2: " + p2);
        System.out.println("p3: " + p3);
        System.out.println("Manhattan distance p1->p2: " + p1.distance(p2));
        System.out.println("p1 adjacent to p2? " + p1.isAdjacentTo(p2));
        System.out.println("p1 adjacent to p3? " + p1.isAdjacentTo(p3));
        System.out.println();
    }
    
    private static void testBoard() {
        System.out.println("--- Test 3: Board Functionality ---");
        Board board = new Board(5, 5);
        Position knightPos = new Position(2, 2);
        
        System.out.println("Testing Knight moves from " + knightPos + ":");
        List<Position> knightMoves = board.getValidMoves(knightPos, PieceType.KNIGHT);
        System.out.println("  Valid moves: " + knightMoves.size());
        for (Position move : knightMoves) {
            System.out.println("    " + move);
        }
        
        System.out.println("\nTesting Queen moves from " + knightPos + ":");
        List<Position> queenMoves = board.getValidMoves(knightPos, PieceType.QUEEN);
        System.out.println("  Valid moves: " + queenMoves.size());
        
        System.out.println("\nTesting capture detection:");
        Position pos1 = new Position(0, 0);
        Position pos2 = new Position(2, 1);
        boolean canCapture = board.canCapture(pos1, PieceType.KNIGHT, pos2, PieceType.KNIGHT);
        System.out.printf("  Knight at %s can capture at %s? %s%n", pos1, pos2, canCapture);
        
        Position pos3 = new Position(0, 0);
        Position pos4 = new Position(3, 3);
        boolean canCaptureQueen = board.canCapture(pos3, PieceType.QUEEN, pos4, PieceType.KNIGHT);
        System.out.printf("  Queen at %s can capture at %s? %s%n", pos3, pos4, canCaptureQueen);
        System.out.println();
    }
    
    private static void testSolverSmall() {
        System.out.println("--- Test 4: Solve 4x4 Board (2 Knights) ---");
        Board board = new Board(4, 4);
        Position p1Start = new Position(0, 0);
        Position p2Start = new Position(0, 1);
        
        BacktrackingSolver solver = new BacktrackingSolver(
            board, 
            PieceType.KNIGHT, p1Start,
            PieceType.KNIGHT, p2Start
        );
        
        // Add progress callback
        solver.setProgressCallback(visitedCount -> {
            if (solver.getMetrics().getIterations() % 5000 == 0) {
                System.out.printf("  Progress: %d/%d cells, %d iterations%n",
                    visitedCount, board.getTotalCells(), solver.getMetrics().getIterations());
            }
        });
        
        System.out.println("Board: 4x4");
        System.out.println("Piece 1: Knight at " + p1Start);
        System.out.println("Piece 2: Knight at " + p2Start);
        System.out.println("Solving...");
        
        boolean found = solver.solve();
        
        System.out.println("\nResult: " + (found ? "SOLUTION FOUND!" : "No solution"));
        System.out.println("Metrics: " + solver.getMetrics());
        
        if (found) {
            HamiltonianPath solution = solver.getSolution();
            System.out.println("Solution path length: " + solution.getLength());
            System.out.println("First 10 moves:");
            for (int i = 0; i < Math.min(10, solution.getLength()); i++) {
                System.out.printf("  Step %d: %s (Piece %d)%n", 
                    i, solution.getPosition(i), solution.getPieceAtStep(i));
            }
            
            // Print board state
            System.out.println("\nFinal board state:");
            System.out.println(board);
        }
        System.out.println();
    }
    
    private static void testSolverMedium() {
        System.out.println("--- Test 5: Solve 5x5 Board (Knight + Rook) ---");
        Board board = new Board(5, 5);
        Position p1Start = new Position(0, 0);
        Position p2Start = new Position(4, 4);
        
        BacktrackingSolver solver = new BacktrackingSolver(
            board, 
            PieceType.KNIGHT, p1Start,
            PieceType.ROOK, p2Start
        );
        
        // Add progress callback
        solver.setProgressCallback(visitedCount -> {
            if (solver.getMetrics().getIterations() % 10000 == 0) {
                System.out.printf("  Progress: %d/%d cells, %d iterations, %.1fs%n",
                    visitedCount, board.getTotalCells(), 
                    solver.getMetrics().getIterations(),
                    solver.getMetrics().getElapsedTimeSeconds());
            }
        });
        
        System.out.println("Board: 5x5");
        System.out.println("Piece 1: Knight at " + p1Start);
        System.out.println("Piece 2: Rook at " + p2Start);
        System.out.println("Solving... (this may take a while)");
        
        boolean found = solver.solve();
        
        System.out.println("\nResult: " + (found ? "SOLUTION FOUND!" : "No solution"));
        System.out.println("Metrics: " + solver.getMetrics());
        
        if (found) {
            HamiltonianPath solution = solver.getSolution();
            System.out.println("Solution: " + solution);
        }
        System.out.println();
    }
}
