package com.serafinebot.p2;

import com.serafinebot.p2.model.*;

import java.util.List;

/**
 * Visual demonstration of board functionality.
 */
public class DemoBoard {
    
    public static void main(String[] args) {
        System.out.println("=== Board Functionality Demo ===\n");
        
        // Demo 1: Knight movement visualization
        demoKnightMovement();
        
        // Demo 2: Queen movement visualization
        demoQueenMovement();
        
        // Demo 3: Capture detection
        demoCaptureDetection();
        
        // Demo 4: Warnsdorff's heuristic
        demoWarnsdorffsHeuristic();
    }
    
    private static void demoKnightMovement() {
        System.out.println("--- Demo 1: Knight Movement (STATIC) ---");
        Board board = new Board(5, 5);
        Position knightPos = new Position(2, 2);
        
        System.out.println("Knight at " + knightPos + ":");
        List<Position> moves = board.getValidMoves(knightPos, PieceType.KNIGHT);
        
        // Visualize board with moves
        boolean[][] moveMap = new boolean[5][5];
        for (Position move : moves) {
            moveMap[move.row()][move.col()] = true;
        }
        moveMap[knightPos.row()][knightPos.col()] = false;  // Mark knight position specially
        
        System.out.println("\n  Board (K=Knight, *=Valid Move, .=Empty):");
        for (int r = 0; r < 5; r++) {
            System.out.print("  ");
            for (int c = 0; c < 5; c++) {
                if (r == knightPos.row() && c == knightPos.col()) {
                    System.out.print("K ");
                } else if (moveMap[r][c]) {
                    System.out.print("* ");
                } else {
                    System.out.print(". ");
                }
            }
            System.out.println();
        }
        System.out.println("  Total valid moves: " + moves.size());
        System.out.println();
    }
    
    private static void demoQueenMovement() {
        System.out.println("--- Demo 2: Queen Movement (CONTINUOUS) ---");
        Board board = new Board(5, 5);
        Position queenPos = new Position(2, 2);
        
        // Mark some cells as visited to show how Queen avoids them
        board.setVisited(new Position(2, 0));
        board.setVisited(new Position(0, 0));
        
        System.out.println("Queen at " + queenPos + " (some cells already visited):");
        List<Position> moves = board.getValidMoves(queenPos, PieceType.QUEEN);
        
        // Visualize board
        boolean[][] moveMap = new boolean[5][5];
        for (Position move : moves) {
            moveMap[move.row()][move.col()] = true;
        }
        
        System.out.println("\n  Board (Q=Queen, *=Valid Move, X=Visited, .=Empty):");
        for (int r = 0; r < 5; r++) {
            System.out.print("  ");
            for (int c = 0; c < 5; c++) {
                Position pos = new Position(r, c);
                if (r == queenPos.row() && c == queenPos.col()) {
                    System.out.print("Q ");
                } else if (board.isVisited(pos)) {
                    System.out.print("X ");
                } else if (moveMap[r][c]) {
                    System.out.print("* ");
                } else {
                    System.out.print(". ");
                }
            }
            System.out.println();
        }
        System.out.println("  Total valid moves: " + moves.size());
        System.out.println();
    }
    
    private static void demoCaptureDetection() {
        System.out.println("--- Demo 3: Capture Detection ---");
        Board board = new Board(5, 5);
        
        Position knightPos = new Position(2, 2);
        Position target1 = new Position(0, 1);  // Can capture
        Position target2 = new Position(0, 0);  // Cannot capture
        
        boolean canCapture1 = board.canCapture(knightPos, PieceType.KNIGHT, target1, null);
        boolean canCapture2 = board.canCapture(knightPos, PieceType.KNIGHT, target2, null);
        
        System.out.printf("Knight at %s:%n", knightPos);
        System.out.printf("  Can capture at %s? %s%n", target1, canCapture1);
        System.out.printf("  Can capture at %s? %s%n", target2, canCapture2);
        
        Position queenPos = new Position(0, 0);
        Position target3 = new Position(3, 3);  // Can capture (diagonal)
        Position target4 = new Position(2, 1);  // Cannot capture
        
        boolean canCaptureQ1 = board.canCapture(queenPos, PieceType.QUEEN, target3, null);
        boolean canCaptureQ2 = board.canCapture(queenPos, PieceType.QUEEN, target4, null);
        
        System.out.printf("%nQueen at %s:%n", queenPos);
        System.out.printf("  Can capture at %s? %s (diagonal line)%n", target3, canCaptureQ1);
        System.out.printf("  Can capture at %s? %s (not on any line)%n", target4, canCaptureQ2);
        System.out.println();
    }
    
    private static void demoWarnsdorffsHeuristic() {
        System.out.println("--- Demo 4: Warnsdorff's Heuristic ---");
        Board board = new Board(5, 5);
        Position knightPos = new Position(0, 0);  // Corner position
        
        System.out.println("Knight at corner " + knightPos + ":");
        
        // Get unsorted moves
        List<Position> unsortedMoves = board.getValidMoves(knightPos, PieceType.KNIGHT);
        System.out.println("\nUnsorted moves: " + unsortedMoves.size());
        
        // Get sorted moves (Warnsdorff's heuristic)
        List<Position> sortedMoves = board.getValidMovesSorted(knightPos, PieceType.KNIGHT);
        System.out.println("\nSorted by Warnsdorff's heuristic (fewest exits first):");
        for (Position move : sortedMoves) {
            int exits = board.getValidMoves(move, PieceType.KNIGHT).size();
            System.out.printf("  %s -> %d possible next moves%n", move, exits);
        }
        
        System.out.println("\nWarnsdorff's rule: Prefer moves with fewer onward possibilities.");
        System.out.println("This reduces backtracking by avoiding getting 'trapped' later.");
        System.out.println();
    }
}
