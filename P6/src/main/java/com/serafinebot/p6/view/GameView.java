package com.serafinebot.p6.view;

import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.Move;

public interface GameView {

    void setListener(GameViewListener listener);

    MatchType selectedMatchType();

    AgentType selectedRedAgent();

    AgentType selectedYellowAgent();

    void showState(GameState state);

    void animateMove(GameState before, GameState after, Move move, Runnable onFinished);

    void showMessage(String message);

    void setHumanControlsEnabled(boolean enabled);

    void setPlaybackControls(boolean playEnabled, boolean stopEnabled, boolean nextEnabled, boolean previousEnabled);
}
