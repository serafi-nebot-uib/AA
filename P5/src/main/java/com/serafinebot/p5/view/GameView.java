package com.serafinebot.p5.view;

import com.serafinebot.p5.model.GameInput;
import com.serafinebot.p5.model.GameResult;

/** Minimal operations the controller needs from a calculator-game view. */
public interface GameView {
    void setListener(GameViewListener listener);

    GameInput input();

    void showResult(GameInput input, GameResult result);

    void showError(String message);
}
