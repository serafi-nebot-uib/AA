package com.serafinebot.p6.view;

import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.Move;

/**
 * View-side contract used by the controller.
 *
 * <p>The controller depends on this interface instead of directly depending on
 * {@link GameFrame}. This keeps the MVC boundary explicit: the controller can
 * update the visual state and enable/disable controls, but it does not know how
 * Swing components are internally arranged.</p>
 */
public interface GameView {

    void setListener(GameViewListener listener);

    MatchType selectedMatchType();

    AgentType selectedRedAgent();

    AgentType selectedYellowAgent();

    void showState(GameState state);

    /**
     * Animates a transition between two model states.
     *
     * <p>The model is already updated by the controller before this method is
     * called. The view receives both states so it can draw the visual transition
     * without mutating game logic. {@code onFinished} is called once the visual
     * effect is over, allowing the controller to re-enable input.</p>
     */
    void animateMove(GameState before, GameState after, Move move, Runnable onFinished);

    void showMessage(String message);

    void setHumanControlsEnabled(boolean enabled);

    void setPlaybackControls(boolean playEnabled, boolean stopEnabled, boolean nextEnabled, boolean previousEnabled);
}
