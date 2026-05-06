package com.serafinebot.p5.controller;

import com.serafinebot.p5.model.GameConfig;
import com.serafinebot.p5.model.GameInput;
import com.serafinebot.p5.model.GameResult;
import com.serafinebot.p5.model.GameState;
import com.serafinebot.p5.model.Keypad;
import com.serafinebot.p5.model.KeypadMode;
import com.serafinebot.p5.model.MinimaxSolver;
import com.serafinebot.p5.model.ReplayStep;
import com.serafinebot.p5.view.GameView;
import com.serafinebot.p5.view.GameViewListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Coordinates validation and solving between the GUI and the model layer. */
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
        MinimaxSolver solver = new MinimaxSolver(config);
        int losingPlayerIndex = solver.losingPlayer();
        int winningPlayer = input.playerCount() == 2 ? otherPlayer(losingPlayerIndex) + 1 : 0;

        return new GameResult(
                keypad,
                keypad.moves(input.lastPlayed()),
                losingPlayerIndex + 1,
                winningPlayer,
                solver.memo().size(),
                replay(config, solver)
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

    private List<ReplayStep> replay(GameConfig config, MinimaxSolver solver) {
        List<ReplayStep> replay = new ArrayList<>();
        GameState state = config.initialState();
        replay.add(new ReplayStep(
                0,
                state.total(),
                state.total(),
                state.lastPlayed(),
                0,
                0,
                state.turn() + 1,
                config.keypad().moves(state.lastPlayed()),
                false
        ));

        while (state.total() < config.limit()) {
            int move = solver.chosenMove(state);
            int nextTotal = state.total() + move;
            boolean terminal = nextTotal >= config.limit();
            int nextTurn = (state.turn() + 1) % config.playerCount();
            replay.add(new ReplayStep(
                    replay.size(),
                    state.total(),
                    nextTotal,
                    move,
                    state.turn() + 1,
                    move,
                    terminal ? 0 : nextTurn + 1,
                    terminal ? new int[0] : config.keypad().moves(move),
                    terminal
            ));
            if (terminal) break;
            state = new GameState(nextTotal, move, nextTurn);
        }

        return replay;
    }
}
