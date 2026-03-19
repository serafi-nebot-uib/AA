package com.serafinebot.p2.model;

/**
 * Type of movement for a piece.
 * STATIC: Single-step moves (Knight, King-like)
 * CONTINUOUS: Sliding moves until obstacle (Queen, Rook, Bishop)
 */
public enum MovementType {
    /**
     * Single-step movement (e.g., Knight, Owl, Snake).
     * Each movement vector is applied once.
     */
    STATIC,
    
    /**
     * Continuous movement (e.g., Queen, Rook, Bishop).
     * Each movement vector is repeated until board edge or obstacle.
     */
    CONTINUOUS
}
