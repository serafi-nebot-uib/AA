package com.serafinebot.p6.model;

public enum Cell {
    EMPTY,
    RED,
    YELLOW;

    public static Cell of(Player player) {
        return player == Player.RED ? RED : YELLOW;
    }

    public Player player() {
        return switch (this) {
            case RED -> Player.RED;
            case YELLOW -> Player.YELLOW;
            case EMPTY -> throw new IllegalStateException("Empty cell has no player");
        };
    }
}
