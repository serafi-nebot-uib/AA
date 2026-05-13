package com.serafinebot.p6.controller;

import com.serafinebot.p6.model.GameState;
import com.serafinebot.p6.model.Move;
import com.serafinebot.p6.model.Player;
import com.serafinebot.p6.model.agents.Agent;
import com.serafinebot.p6.model.agents.GreedyAgent;
import com.serafinebot.p6.model.agents.MinimaxAgent;
import com.serafinebot.p6.model.agents.RandomAgent;
import com.serafinebot.p6.view.AgentType;
import com.serafinebot.p6.view.GameView;
import com.serafinebot.p6.view.GameViewListener;
import com.serafinebot.p6.view.MatchType;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/**
 * MVC controller for the playable application.
 *
 * <p>The controller is the only class that knows about both the view and the
 * model. It receives UI events, validates whose turn it is, asks agents for
 * moves when needed, stores the history used by the playback controls, and keeps
 * long-running agent computations outside the Swing event thread.</p>
 */
public final class GameController implements GameViewListener {

    private static final int AUTO_DELAY_MS = 900;

    private final GameView view;
    private final List<GameState> history = new ArrayList<>();
    private final List<Move> moves = new ArrayList<>();
    private GameState state = new GameState();
    private MatchType matchType = MatchType.HUMAN_VS_HUMAN;
    private AgentType redAgentType = AgentType.GREEDY;
    private AgentType yellowAgentType = AgentType.MINIMAX;
    private Agent redAgent;
    private Agent yellowAgent;
    private Timer autoTimer;
    private int cursor;
    private boolean busy;

    public GameController(GameView view) {
        this.view = view;
        this.view.setListener(this);
        configureAgents();
        resetHistory();
        refresh("Nova partida iniciada.");
    }

    @Override
    public void onNewGame(MatchType matchType, AgentType redAgent, AgentType yellowAgent) {
        stopAuto();
        this.matchType = matchType;
        this.redAgentType = redAgent;
        this.yellowAgentType = yellowAgent;
        this.state = new GameState();
        this.busy = false;
        configureAgents();
        resetHistory();
        refresh("Nova partida iniciada.");
    }

    @Override
    public void onDrop(int column) {
        applyHumanMove(Move.drop(column));
    }

    @Override
    public void onRemove(int row, int column) {
        applyHumanMove(Move.remove(row, column));
    }

    @Override
    public void onRotateLeft() {
        applyHumanMove(Move.rotateLeft());
    }

    @Override
    public void onRotateRight() {
        applyHumanMove(Move.rotateRight());
    }

    @Override
    public void onPlay() {
        if (matchType != MatchType.AGENT_VS_AGENT || state.result().isFinished()) {
            refresh("La reproduccio nomes esta disponible en agent contra agent.");
            return;
        }
        if (autoTimer != null && autoTimer.isRunning()) {
            return;
        }
        autoTimer = new Timer(AUTO_DELAY_MS, event -> {
            if (!busy && !state.result().isFinished()) {
                onNext();
            }
            if (state.result().isFinished()) {
                stopAuto();
            }
        });
        autoTimer.start();
        refresh("Reproduccio automatica iniciada.");
    }

    @Override
    public void onStop() {
        stopAuto();
        refresh("Reproduccio aturada.");
    }

    @Override
    public void onNext() {
        if (busy) {
            return;
        }
        if (cursor < moves.size()) {
            replayNext();
            return;
        }
        if (state.result().isFinished()) {
            refresh("La partida ja ha acabat.");
            return;
        }
        if (isHumanTurn()) {
            refresh("Ara juga el jugador huma.");
            return;
        }
        computeAndApplyAgentMove();
    }

    @Override
    public void onPrevious() {
        if (busy || cursor == 0) {
            return;
        }
        stopAuto();
        cursor--;
        state = history.get(cursor);
        refresh("Estat anterior.");
    }

    private void applyHumanMove(Move move) {
        if (busy) {
            refresh("Espera que acabi l'accio actual.");
            return;
        }
        if (!isHumanTurn()) {
            refresh("No es el torn del jugador huma.");
            return;
        }
        applyMove(move, "Moviment huma: " + move, () -> {
            if (!state.result().isFinished() && !isHumanTurn()) {
                SwingUtilities.invokeLater(this::computeAndApplyAgentMove);
            } else {
                refresh("Torn actualitzat.");
            }
        });
    }

    private void computeAndApplyAgentMove() {
        // Agents can be expensive, especially Minimax. SwingWorker keeps the UI
        // responsive while the move is being selected.
        Agent agent = agentFor(state.currentPlayer());
        GameState searchState = state;
        busy = true;
        refresh(agent.name() + " esta pensant...");

        new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() {
                return agent.chooseMove(searchState);
            }

            @Override
            protected void done() {
                try {
                    Move move = get();
                    busy = false;
                    applyMove(move, agentMessage(agent, move), () -> refresh(statusAfterAgent(agent)));
                } catch (Exception ex) {
                    busy = false;
                    refresh("Error de l'agent: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void replayNext() {
        // Replaying an already-known move intentionally has no animation: it is
        // used as history navigation, not as a newly computed game action.
        Move move = moves.get(cursor);
        state = history.get(cursor + 1);
        cursor++;
        refresh("Reproduint: " + move);
    }

    private void applyMove(Move move, String message, Runnable afterAnimation) {
        try {
            GameState before = state;
            GameState after = state.apply(move);
            truncateFuture();
            moves.add(move);
            history.add(after);
            cursor++;
            state = after;
            busy = true;
            refresh(message);
            view.animateMove(before, after, move, () -> {
                busy = false;
                afterAnimation.run();
            });
        } catch (IllegalArgumentException | IllegalStateException ex) {
            refresh("Moviment no valid: " + move);
        }
    }

    private void truncateFuture() {
        // If the user steps back and then makes/computes a different move, the
        // old future is no longer part of the current game line.
        while (history.size() > cursor + 1) {
            history.remove(history.size() - 1);
        }
        while (moves.size() > cursor) {
            moves.remove(moves.size() - 1);
        }
    }

    private void resetHistory() {
        history.clear();
        moves.clear();
        history.add(state);
        cursor = 0;
    }

    private void configureAgents() {
        redAgent = createAgent(redAgentType);
        yellowAgent = createAgent(yellowAgentType);
    }

    private Agent createAgent(AgentType type) {
        return switch (type) {
            case RANDOM -> new RandomAgent();
            case GREEDY -> new GreedyAgent();
            case MINIMAX -> new MinimaxAgent();
        };
    }

    private Agent agentFor(Player player) {
        return player == Player.RED ? redAgent : yellowAgent;
    }

    private boolean isHumanTurn() {
        return (matchType == MatchType.HUMAN_VS_HUMAN
                || (matchType == MatchType.HUMAN_VS_AGENT && state.currentPlayer() == Player.RED))
                && !state.result().isFinished();
    }

    private boolean isAutoRunning() {
        return autoTimer != null && autoTimer.isRunning();
    }

    private void stopAuto() {
        if (autoTimer != null) {
            autoTimer.stop();
            autoTimer = null;
        }
    }

    private String agentMessage(Agent agent, Move move) {
        if (agent instanceof MinimaxAgent minimax) {
            return agent.name() + " juga: " + move + " | nodes=" + minimax.lastNodes()
                    + ", podes=" + minimax.lastPrunes() + ", temps=" + minimax.lastTimeMs() + " ms";
        }
        return agent.name() + " juga: " + move;
    }

    private String statusAfterAgent(Agent agent) {
        if (state.result().isFinished()) {
            stopAuto();
            return "Partida finalitzada.";
        }
        if (agent instanceof MinimaxAgent minimax) {
            return agent.name() + ": nodes=" + minimax.lastNodes()
                    + ", podes=" + minimax.lastPrunes() + ", temps=" + minimax.lastTimeMs() + " ms";
        }
        return "Torn actualitzat.";
    }

    private void refresh(String message) {
        view.showState(state);
        view.showMessage(message);
        view.setHumanControlsEnabled(isHumanTurn() && !busy && !isAutoRunning());
        view.setPlaybackControls(
                matchType == MatchType.AGENT_VS_AGENT && !busy && !isAutoRunning() && !state.result().isFinished(),
                isAutoRunning(),
                !busy && !state.result().isFinished() && !isHumanTurn(),
                !busy && cursor > 0
        );
    }
}
