package com.serafinebot.p5.controller;

import com.serafinebot.p5.model.GameInput;
import com.serafinebot.p5.model.GameResult;
import com.serafinebot.p5.model.KeypadMode;
import com.serafinebot.p5.model.ReplayStep;
import com.serafinebot.p5.model.SolverMode;
import com.serafinebot.p5.view.GameView;
import com.serafinebot.p5.view.GameViewListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameControllerTest {

    @Test
    void solvesProblemStatementExampleUsingUiPlayerNumbers() {
        FakeView view = new FakeView(new GameInput(3, 3, 28, 31, 6, 2, 1));

        new GameController(view);

        assertEquals(1, view.result.losingPlayer());
        assertEquals(2, view.result.winningPlayer());
    }

    @Test
    void buildsTerminalReplayForProblemStatementExample() {
        GameResult result = controller.solve(new GameInput(3, 3, 28, 31, 6, 2, 1));
        ReplayStep terminal = result.replay().get(result.replay().size() - 1);

        assertEquals(2, result.replay().size());
        assertEquals(1, terminal.movedPlayer());
        assertEquals(3, terminal.move());
        assertEquals(28, terminal.totalBefore());
        assertEquals(31, terminal.total());
        assertTrue(terminal.terminal());
    }

    @Test
    void randomKeypadIsDeterministicForSameSeed() {
        GameInput first = new GameInput(3, 3, 0, 31, 0, 2, 1, KeypadMode.RANDOM, 1234L);
        GameInput second = new GameInput(3, 3, 0, 31, 0, 2, 1, KeypadMode.RANDOM, 1234L);

        assertEquals(controller.solve(first).keypad().toString(), controller.solve(second).keypad().toString());
    }

    @Test
    void bottomUpModeMatchesTopDownResult() {
        GameInput topDown = new GameInput(3, 3, 0, 31, 0, 2, 1);
        GameInput bottomUp = new GameInput(3, 3, 0, 31, 0, 2, 1, KeypadMode.STANDARD, 0L, SolverMode.BOTTOM_UP_DP);

        assertEquals(controller.solve(topDown).losingPlayer(), controller.solve(bottomUp).losingPlayer());
        assertEquals(controller.solve(topDown).replay(), controller.solve(bottomUp).replay());
    }

    @Test
    void rejectsInvalidCurrentPosition() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.solve(new GameInput(3, 3, 31, 31, 0, 2, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> controller.solve(new GameInput(3, 3, 0, 31, 10, 2, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> controller.solve(new GameInput(3, 3, 0, 31, 0, 2, 3)));
    }

    private final GameController controller = new GameController(new FakeView(new GameInput(3, 3, 0, 31, 0, 2, 1)));

    private static final class FakeView implements GameView {
        private final GameInput input;
        private GameResult result;
        private String error;
        private GameViewListener listener;

        private FakeView(GameInput input) {
            this.input = input;
        }

        @Override
        public void setListener(GameViewListener listener) {
            this.listener = listener;
        }

        @Override
        public GameInput input() {
            return input;
        }

        @Override
        public void showResult(GameInput input, GameResult result) {
            this.result = result;
        }

        @Override
        public void showError(String message) {
            this.error = message;
        }
    }
}
