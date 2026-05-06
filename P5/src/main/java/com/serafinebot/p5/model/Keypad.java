package com.serafinebot.p5.model;

import java.util.*;

public class Keypad {
    public static final int DIM_MIN = 2;
    public static final int DIM_MAX = 5;

    private final int width;
    private final int height;
    private final int[] values;
    private final int[][] moves;

    public Keypad(int width, int height, int[] values) {
        // check keypad dimension bounds
        if (width < DIM_MIN || width > DIM_MAX)
            throw new IllegalArgumentException("width out of range [" + DIM_MIN + ", " + DIM_MAX + "]: " + width);
        if (height < DIM_MIN || height > DIM_MAX)
            throw new IllegalArgumentException("height out of range [" + DIM_MIN + ", " + DIM_MAX + "]: " + height);

        // check keypad value count
        int count = width * height;
        if (values.length < count)
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

        /*

         0   1   2   3   4
         5   6   7   8   9
        10  11  12  13  14
        15  16  17  18  19
        20  21  22  23  24

        row = v / width
        col = v % width

        [row * width, row * width + width - 1]
        [0..height] * width + col

        */

        this.width = width;
        this.height = height;
        this.values = values;
        this.moves = new int[count][width + height - 1];

        // calculate valid moves from every position
        for (int i = 0; i < values.length; i++) {
            int j = 0;
            int v = values[i];
            int row = i / width;
            int col = i % width;
            for (int a = row * width; a < (row + 1) * width; a++) moves[v-1][j++] = values[a];
            for (int h = 0; h < height; h++) if (h != row) moves[v-1][j++] = values[h * width + col];
        }
    }

    public static Keypad standard(int width, int height) {
        int[] values = new int[width * height];
        for (int i = 0; i < values.length; i++) values[i] = i+1;
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
        if (last == 0) return values;
        if (!contains(last)) return null;
        return moves[last - 1];
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
