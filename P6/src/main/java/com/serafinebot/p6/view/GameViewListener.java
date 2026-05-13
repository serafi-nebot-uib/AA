package com.serafinebot.p6.view;

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
