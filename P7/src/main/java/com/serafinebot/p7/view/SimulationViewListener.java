package com.serafinebot.p7.view;

import com.serafinebot.p7.model.RuleVariant;

/**
 * Events emitted by the Swing view.
 *
 * <p>The view sends only primitive/configuration values to the controller. It
 * never creates simulations itself, which keeps all model access in the
 * controller layer.</p>
 */
public interface SimulationViewListener {

    /**
     * Requests a simulation batch from the current GUI configuration.
     */
    void onRunRequested(int games, String seedText, RuleVariant variant, int threadCount,
                        int playerCount, boolean compareVariants);
}
