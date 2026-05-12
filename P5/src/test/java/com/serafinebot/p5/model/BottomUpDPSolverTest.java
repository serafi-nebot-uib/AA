package com.serafinebot.p5.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomUpDPSolverTest {

    @Test
    void matchesTopDownForRepresentativeConfigurations() {
        assertMatchesTopDown(new GameConfig(Keypad.standard(3, 3), 0, 31, 0, 2, 0));
        assertMatchesTopDown(new GameConfig(Keypad.standard(3, 3), 28, 31, 6, 2, 0));
        assertMatchesTopDown(new GameConfig(Keypad.standard(2, 2), 0, 5, 0, 3, 0));
        assertMatchesTopDown(new GameConfig(new Keypad(4, 3, new int[]{12, 2, 8, 4, 6, 1, 10, 3, 7, 11, 5, 9}), 4, 25, 10, 2, 1));
    }

    @Test
    void precomputesWholeStateSpace() {
        GameConfig config = new GameConfig(Keypad.standard(3, 3), 0, 31, 0, 2, 0);
        BottomUpDPSolver solver = new BottomUpDPSolver(config);

        assertEquals(31 * 10 * 2, solver.stateCount());
    }

    @Test
    void replayEndsWithTheComputedLoser() {
        GameConfig config = new GameConfig(Keypad.standard(3, 3), 0, 31, 0, 2, 0);
        BottomUpDPSolver solver = new BottomUpDPSolver(config);
        ReplayStep terminal = solver.replay().get(solver.replay().size() - 1);

        assertTrue(terminal.terminal());
        assertEquals(solver.losingPlayer() + 1, terminal.movedPlayer());
    }

    private void assertMatchesTopDown(GameConfig config) {
        TopDownDPSolver topDown = new TopDownDPSolver(config);
        BottomUpDPSolver bottomUp = new BottomUpDPSolver(config);
        GameState initial = config.initialState();

        assertEquals(topDown.losingPlayer(), bottomUp.losingPlayer());
        assertEquals(topDown.chosenMove(initial), bottomUp.chosenMove(initial));
        assertEquals(topDown.replay(), bottomUp.replay());
    }
}
