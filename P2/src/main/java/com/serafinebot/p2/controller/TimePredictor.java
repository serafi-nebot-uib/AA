package com.serafinebot.p2.controller;

import com.serafinebot.p2.model.PieceType;

import java.awt.Color;

/**
 * Time prediction based on an empirical exponential model.
 * <p>
 * Formula: {@code T(n) = k * e^(α * n) * avgComplexity}
 * where {@code n = rows * cols} and complexity comes from
 * {@link PieceType#getComplexity()}.
 * <p>
 * Constants {@code k} and {@code α} should be calibrated against real
 * execution measurements (see PLAN.md, Appendix B).
 */
public final class TimePredictor {

    // Empirical constants (to be calibrated with real measurements)
    private static final double BASE_CONSTANT = 0.001;   // k  (seconds)
    private static final double GROWTH_FACTOR = 0.35;    // α  (exponential rate)

    // Difficulty thresholds (seconds)
    private static final double VERY_EASY_LIMIT  = 1;
    private static final double EASY_LIMIT       = 10;
    private static final double MODERATE_LIMIT   = 60;
    private static final double DIFFICULT_LIMIT  = 300;

    // Difficulty colours
    private static final Color COLOR_VERY_EASY      = new Color(0, 150, 0);
    private static final Color COLOR_EASY           = new Color(100, 160, 0);
    private static final Color COLOR_MODERATE       = new Color(200, 150, 0);
    private static final Color COLOR_DIFFICULT      = new Color(200, 80, 0);
    private static final Color COLOR_VERY_DIFFICULT = new Color(200, 0, 0);

    /** Utility class — no instantiation. */
    private TimePredictor() {}

    // =====================================================================
    //  Prediction
    // =====================================================================

    /**
     * Predict execution time in seconds.
     *
     * @param rows   Board height
     * @param cols   Board width
     * @param piece1 First piece type
     * @param piece2 Second piece type
     * @return Estimated time in seconds
     */
    public static double predictTime(int rows, int cols,
                                     PieceType piece1, PieceType piece2) {
        int totalCells = rows * cols;
        double avgComplexity = (piece1.getComplexity() + piece2.getComplexity()) / 2.0;
        return BASE_CONSTANT * Math.exp(GROWTH_FACTOR * totalCells) * avgComplexity;
    }

    /**
     * Get a human-readable time estimate in Catalan.
     *
     * @return e.g. "&lt; 1 segon", "5.3 segons", "2.1 minuts", "1.2 hores"
     */
    public static String predictTimeFormatted(int rows, int cols,
                                              PieceType piece1, PieceType piece2) {
        double seconds = predictTime(rows, cols, piece1, piece2);

        if (seconds < 1) {
            return "< 1 segon";
        } else if (seconds < 60) {
            return String.format("%.1f segons", seconds);
        } else if (seconds < 3600) {
            return String.format("%.1f minuts", seconds / 60);
        } else {
            return String.format("%.1f hores", seconds / 3600);
        }
    }

    // =====================================================================
    //  Difficulty
    // =====================================================================

    /**
     * Get difficulty level label in Catalan.
     *
     * @return One of "Molt fàcil", "Fàcil", "Moderat", "Difícil", "Molt difícil"
     */
    public static String getDifficultyLevel(int rows, int cols,
                                            PieceType piece1, PieceType piece2) {
        double seconds = predictTime(rows, cols, piece1, piece2);

        if (seconds < VERY_EASY_LIMIT) {
            return "Molt fàcil";
        } else if (seconds < EASY_LIMIT) {
            return "Fàcil";
        } else if (seconds < MODERATE_LIMIT) {
            return "Moderat";
        } else if (seconds < DIFFICULT_LIMIT) {
            return "Difícil";
        } else {
            return "Molt difícil";
        }
    }

    /**
     * Get a colour that visually matches the difficulty level.
     *
     * @return Green (easy) through red (very difficult)
     */
    public static Color getDifficultyColor(int rows, int cols,
                                           PieceType piece1, PieceType piece2) {
        double seconds = predictTime(rows, cols, piece1, piece2);

        if (seconds < VERY_EASY_LIMIT) {
            return COLOR_VERY_EASY;
        } else if (seconds < EASY_LIMIT) {
            return COLOR_EASY;
        } else if (seconds < MODERATE_LIMIT) {
            return COLOR_MODERATE;
        } else if (seconds < DIFFICULT_LIMIT) {
            return COLOR_DIFFICULT;
        } else {
            return COLOR_VERY_DIFFICULT;
        }
    }
}
