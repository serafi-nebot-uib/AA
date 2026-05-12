package com.serafinebot.p5.view;

import com.serafinebot.p5.model.GameInput;
import com.serafinebot.p5.model.GameResult;

/**
 * Minimal operations the controller needs from a calculator-game view.
 *
 * <p>This interface keeps the controller independent from Swing. The controller
 * receives user events through {@link GameViewListener}, pulls the current input
 * through {@link #input()}, and pushes either a result or a validation error.
 */
public interface GameView {
    /** Registers the controller-side event listener. */
    void setListener(GameViewListener listener);

    /** Current form state, using the model's display-facing {@link GameInput}. */
    GameInput input();

    /** Renders a successful solve result. */
    void showResult(GameInput input, GameResult result);

    /** Renders a validation or solving error. */
    void showError(String message);
}
