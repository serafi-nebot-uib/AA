package com.serafinebot.p7.view;

import com.serafinebot.p7.model.SimulationResult;

/**
 * View contract used by the controller.
 *
 * <p>The controller depends on this interface instead of directly depending on
 * {@link SimulationFrame}. This keeps the MVC boundary explicit: the controller
 * can start/stop visual busy states and deliver results, but it does not know how
 * Swing widgets are arranged internally.</p>
 */
public interface SimulationView {

    /** Registers the controller-side listener for user actions. */
    void setListener(SimulationViewListener listener);

    /** Enables or disables input controls while a background simulation runs. */
    void setRunning(boolean running);

    /** Renders one completed simulation batch. */
    void showResult(SimulationResult result);

    /** Shows a validation or execution error to the user. */
    void showError(String message);
}
