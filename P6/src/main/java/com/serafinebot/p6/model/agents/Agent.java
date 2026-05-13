package com.serafinebot.p6.model.agents;

import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.Move;

/**
 * Common contract for every automatic player used by the application.
 *
 * <p>Agents are intentionally kept independent from Swing and from the
 * controller. They receive a complete immutable {@link GameState}, inspect its
 * legal moves, and return one move to apply. This makes the same agents usable
 * in the GUI, in agent-vs-agent matches, and later in benchmark experiments.</p>
 */
public interface Agent {

    /**
     * Human-readable name shown in the UI and benchmark reports.
     */
    String name();

    /**
     * Selects one legal move for the current player in {@code state}.
     *
     * @param state current game state; it must not be modified by the agent
     * @return the chosen legal move
     * @throws IllegalStateException if the state has no legal moves
     */
    Move chooseMove(GameState state);
}
