package com.serafinebot.p2.model;

/**
 * Enum representing all available piece types with their movement patterns.
 * Each piece defines:
 * - Catalan name for GUI display
 * - Short name (abbreviation)
 * - Movement type (STATIC or CONTINUOUS)
 * - Movement vectors
 * - Complexity factor for time prediction
 */
public enum PieceType {
    
    KNIGHT("Cavall", "C", MovementType.STATIC, new int[][] {
        {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
        {1, -2}, {1, 2}, {2, -1}, {2, 1}
    }),
    
    QUEEN("Reina", "Q", MovementType.CONTINUOUS, new int[][] {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1}
    }),
    
    ROOK("Torre", "T", MovementType.CONTINUOUS, new int[][] {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    }),
    
    BISHOP("Alfil", "A", MovementType.CONTINUOUS, new int[][] {
        {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    }),
    
    OWL("Mussol", "M", MovementType.STATIC, new int[][] {
        {-3, -1}, {-3, 1}, {-1, -3}, {-1, 3},
        {1, -3}, {1, 3}, {3, -1}, {3, 1}
    }),

    SNAKE("Serp", "S", MovementType.STATIC, new int[][] {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    });
    
    // Fields
    private final String name;           // Catalan name for GUI
    private final String shortName;      // Abbreviation (C, Q, T, A, M, D)
    private final MovementType movementType;
    private final int[][] movements;
    
    /**
     * Constructor for each enum constant.
     */
    PieceType(String name, String shortName, MovementType movementType, int[][] movements) {
        this.name = name;
        this.shortName = shortName;
        this.movementType = movementType;
        this.movements = movements;
    }
    
    // Getters
    
    /**
     * Get Catalan name for GUI display.
     * @return Catalan name (e.g., "Cavall", "Reina")
     */
    public String getName() { 
        return name; 
    }
    
    /**
     * Get short abbreviation.
     * @return Single letter (e.g., "C", "Q")
     */
    public String getShortName() { 
        return shortName; 
    }
    
    /**
     * Get movement type.
     * @return STATIC or CONTINUOUS
     */
    public MovementType getMovementType() { 
        return movementType; 
    }
    
    /**
     * Get movement vectors.
     * @return 2D array of [deltaRow, deltaCol] offsets
     */
    public int[][] getMovements() { 
        return movements; 
    }
    
    /**
     * Get image path for this piece.
     *
     * @param isWhite true for white variant, false for black
     * @return classpath resource path (e.g., "/resources/knight-white.png")
     */
    public String getImagePath(boolean isWhite) {
        String color = isWhite ? "white" : "black";
        return String.format("/resources/%s-%s.png",
                             name().toLowerCase(), color);
    }
    
    /**
     * String representation (Catalan name for GUI ComboBox).
     * @return Catalan name
     */
    @Override
    public String toString() {
        return name;  // Returns Catalan name for GUI ComboBox
    }
}
