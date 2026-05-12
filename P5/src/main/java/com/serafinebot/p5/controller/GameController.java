package com.serafinebot.p5.controller;

import com.serafinebot.p5.model.GameConfig;
import com.serafinebot.p5.model.GameInput;
import com.serafinebot.p5.model.GameResult;
import com.serafinebot.p5.model.BottomUpDPSolver;
import com.serafinebot.p5.model.Keypad;
import com.serafinebot.p5.model.KeypadMode;
import com.serafinebot.p5.model.Solver;
import com.serafinebot.p5.model.SolverMode;
import com.serafinebot.p5.model.TopDownDPSolver;
import com.serafinebot.p5.view.GameView;
import com.serafinebot.p5.view.GameViewListener;

import java.util.Random;

/**
 * Coordinates validation and solving between the GUI and the model layer.
 *
 * <p>The controller is the only layer that knows both the view contract and the
 * game model. The Swing view reports user events through {@link GameViewListener};
 * this class reads the current {@link GameInput}, builds a validated
 * {@link GameConfig}, runs the selected DP solver, and sends a display-ready
 * {@link GameResult} back to the view.
 */
public final class GameController implements GameViewListener {

    private final GameView view;

    public GameController(GameView view) {
        if (view == null) throw new IllegalArgumentException("view must not be null");
        this.view = view;
        this.view.setListener(this);
        configurationChanged();
    }

    @Override
    public void configurationChanged() {
        try {
            GameInput input = view.input();
            view.showResult(input, solve(input));
        } catch (IllegalArgumentException ex) {
            view.showError(ex.getMessage());
        }
    }

    /** Solves one complete user-selected configuration without touching Swing. */
    public GameResult solve(GameInput input) {
        if (input == null) throw new IllegalArgumentException("La configuracio no pot ser buida.");

        Keypad keypad = keypad(input);
        validate(input, keypad);

        int startingPlayerIndex = input.startingPlayer() - 1;
        GameConfig config = new GameConfig(
                keypad,
                input.initialTotal(),
                input.limit(),
                input.lastPlayed(),
                input.playerCount(),
                startingPlayerIndex
        );
        Solver solver = solver(config, input.solverMode());
        int losingPlayerIndex = solver.losingPlayer();
        int winningPlayer = input.playerCount() == 2 ? otherPlayer(losingPlayerIndex) + 1 : 0;
        var replay = solver.replay();

        return new GameResult(
                keypad,
                keypad.moves(input.lastPlayed()),
                losingPlayerIndex + 1,
                winningPlayer,
                solver.stateCount(),
                replay
        );
    }

    private void validate(GameInput input, Keypad keypad) {
        if (input.limit() <= input.initialTotal()) {
            throw new IllegalArgumentException("El limit ha de ser superior al nombre inicial.");
        }
        if (input.lastPlayed() != 0 && !keypad.contains(input.lastPlayed())) {
            throw new IllegalArgumentException("El darrer nombre jugat ha de ser 0 o un valor del teclat.");
        }
        if (input.playerCount() < 2) {
            throw new IllegalArgumentException("Hi ha d'haver com a minim 2 jugadors.");
        }
        if (input.startingPlayer() < 1 || input.startingPlayer() > input.playerCount()) {
            throw new IllegalArgumentException("El jugador inicial ha d'estar entre 1 i el nombre de jugadors.");
        }
    }

    private int otherPlayer(int player) {
        return player == 0 ? 1 : 0;
    }

    private Keypad keypad(GameInput input) {
        if (input.keypadMode() == KeypadMode.RANDOM) {
            return Keypad.random(input.width(), input.height(), new Random(input.keypadSeed()));
        }
        return Keypad.standard(input.width(), input.height());
    }

    private Solver solver(GameConfig config, SolverMode mode) {
        if (mode == SolverMode.BOTTOM_UP_DP) return new BottomUpDPSolver(config);
        return new TopDownDPSolver(config);
    }
}
