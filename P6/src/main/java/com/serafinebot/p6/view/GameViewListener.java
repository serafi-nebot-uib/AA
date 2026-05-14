package com.serafinebot.p6.view;

/**
 * Controller callbacks exposed to Swing components.
 *
 * <p>User interactions are translated into these semantic events. The view does
 * not apply moves directly; it only reports intent. The controller then checks
 * turn ownership, legality and mode-specific behavior.</p>
 */
public interface GameViewListener {

    void onNewGame(MatchType matchType, AgentType redAgent, AgentType yellowAgent);

    void onDrop(int column);

    void onRemove(int row, int column);

    void onRotateLeft();

    void onRotateRight();

    void onPlay();

    void onStop();

    void onNext();

    void onPrevious();
}
