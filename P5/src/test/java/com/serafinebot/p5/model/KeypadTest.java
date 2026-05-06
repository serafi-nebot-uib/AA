package com.serafinebot.p5.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeypadTest {

    @Test
    void movesMatchProblemStatementExample() {
        Keypad keypad = new Keypad(3, 3, new int[]{7, 8, 9, 4, 5, 6, 1, 2, 3});

        assertArrayEquals(new int[]{3, 4, 5, 9}, keypad.moves(6));
    }

    @Test
    void firstMoveCanUseAnyKeyInAscendingOrder() {
        Keypad keypad = new Keypad(2, 3, new int[]{6, 1, 5, 2, 4, 3});

        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, keypad.moves(0));
    }

    @Test
    void standardLayoutUsesCalculatorOrientation() {
        Keypad keypad = Keypad.standard(3, 3);

        assertArrayEquals(new int[]{7, 8, 9, 4, 5, 6, 1, 2, 3}, new int[]{
                keypad.value(0, 0), keypad.value(0, 1), keypad.value(0, 2),
                keypad.value(1, 0), keypad.value(1, 1), keypad.value(1, 2),
                keypad.value(2, 0), keypad.value(2, 1), keypad.value(2, 2)
        });
    }

    @Test
    void rejectsInvalidLayouts() {
        assertThrows(IllegalArgumentException.class, () -> new Keypad(3, 3, new int[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> new Keypad(2, 2, new int[]{1, 2, 2, 4}));
        assertThrows(IllegalArgumentException.class, () -> new Keypad(2, 2, new int[]{1, 2, 3, 5}));
    }
}
