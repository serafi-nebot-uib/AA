package com.serafinebot.p5.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MinimaxSolverTest {

    @Test
    void currentPlayerLosesInProblemStatementExample() {
        GameConfig config = new GameConfig(
                new Keypad(3, 3, new int[]{7, 8, 9, 4, 5, 6, 1, 2, 3}),
                28,
                31,
                6,
                2,
                0
        );

        assertEquals(0, new MinimaxSolver(config).losingPlayer());
    }

    @Test
    void currentPlayerCanForceNextPlayerToLose() {
        GameConfig config = new GameConfig(Keypad.standard(2, 2), 0, 5, 0, 2, 0);

        assertEquals(1, new MinimaxSolver(config).losingPlayer());
    }

    @Test
    void supportsMoreThanTwoPlayers() {
        GameConfig config = new GameConfig(Keypad.standard(2, 2), 0, 5, 0, 3, 0);

        assertEquals(2, new MinimaxSolver(config).losingPlayer());
    }

    @Test
    void memoizesSolvedStates() {
        MinimaxSolver solver = new MinimaxSolver(new GameConfig(Keypad.standard(2, 2), 0, 5, 0, 2, 0));

        solver.losingPlayer();

        assertFalse(solver.memo().isEmpty());
    }
}
