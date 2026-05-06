package com.serafinebot.p5.model;

import java.util.Arrays;
import java.util.Random;

/**
 * Immutable calculator keypad layout and precomputed legal moves for each key.
 */
public class Keypad {
    public static final int DIM_MIN = 2;
    public static final int DIM_MAX = 5;

    private final int width;
    private final int height;
    private final int[] values;
    private final int[] initialMoves;
    private final int[][] moves;

    public Keypad(int width, int height, int[] values) {
        // check keypad dimension bounds
        if (width < DIM_MIN || width > DIM_MAX)
            throw new IllegalArgumentException("width out of range [" + DIM_MIN + ", " + DIM_MAX + "]: " + width);
        if (height < DIM_MIN || height > DIM_MAX)
            throw new IllegalArgumentException("height out of range [" + DIM_MIN + ", " + DIM_MAX + "]: " + height);

        // check keypad value count
        int count = width * height;
        if (values.length != count)
            throw new IllegalArgumentException("invalid value array count: " + values.length + " != " + count);

        // check that all values are unique and in bounds
        boolean[] seen = new boolean[count];
        for (int v : values) {
            if (v < 1 || v > count)
                throw new IllegalArgumentException("grid value " + v + " outside 1.." + count);
            if (seen[v-1])
                throw new IllegalArgumentException("grid contains duplicate value " + v);
            seen[v-1] = true;
        }

        this.width = width;
        this.height = height;
        this.values = Arrays.copyOf(values, values.length);
        this.initialMoves = Arrays.copyOf(values, values.length);
        this.moves = new int[count][width + height - 2];
        Arrays.sort(initialMoves);

        // A key unlocks its row and column, excluding the key just played.
        for (int i = 0; i < values.length; i++) {
            int j = 0;
            int v = values[i];
            int row = i / width;
            int col = i % width;
            for (int a = row * width; a < (row + 1) * width; a++) if (a != i) moves[v-1][j++] = values[a];
            for (int h = 0; h < height; h++) if (h != row) moves[v-1][j++] = values[h * width + col];
            Arrays.sort(moves[v - 1]);
        }
    }

    public static Keypad standard(int width, int height) {
        int[] values = new int[width * height];
        for (int row = 0; row < height; row++) {
            int base = (height - row - 1) * width;
            for (int col = 0; col < width; col++) values[row * width + col] = base + col + 1;
        }
        return new Keypad(width, height, values);
    }

    public static Keypad random(int width, int height, Random rng) {
        int[] values = new int[width * height];
        for (int i = 0; i < values.length; i++) values[i] = i+1;
        for (int i = 0; i < values.length; i++) {
            int src = rng.nextInt(values.length);
            int dst = rng.nextInt(values.length);
            int tmp = values[src];
            values[src] = values[dst];
            values[dst] = tmp;
        }
        return new Keypad(width, height, values);
    }

    public static Keypad random(int width, int height) {
        return random(width, height, new Random());
    }

    public boolean contains(int value) {
        return value >= 1 && value <= width * height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] moves(int last) {
        if (last == 0) return Arrays.copyOf(initialMoves, initialMoves.length);
        if (!contains(last)) return null;
        return Arrays.copyOf(moves[last - 1], moves[last - 1].length);
    }

    public int value(int row, int col) {
        return values[row * width + col];
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++)
                builder.append(String.format("%4d", values[i * width + j]));
            builder.append("\n");
        }
        return builder.toString();
    }
}
