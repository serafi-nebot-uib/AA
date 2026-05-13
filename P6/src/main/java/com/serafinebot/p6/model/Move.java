package com.serafinebot.p6.model;

/**
 * Value object describing one legal action.
 *
 * <p>Only some coordinates are meaningful depending on the type: drops use the
 * column, removals use row and column, and rotations ignore both coordinates.</p>
 */
public record Move(MoveType type, int row, int column) {

    public static Move drop(int column) {
        return new Move(MoveType.DROP, -1, column);
    }

    public static Move remove(int row, int column) {
        return new Move(MoveType.REMOVE, row, column);
    }

    public static Move rotateLeft() {
        return new Move(MoveType.ROTATE_LEFT, -1, -1);
    }

    public static Move rotateRight() {
        return new Move(MoveType.ROTATE_RIGHT, -1, -1);
    }

    @Override
    public String toString() {
        return switch (type) {
            case DROP -> "DROP(" + column + ")";
            case REMOVE -> "REMOVE(" + row + "," + column + ")";
            case ROTATE_LEFT -> "ROTATE_LEFT";
            case ROTATE_RIGHT -> "ROTATE_RIGHT";
        };
    }
}
