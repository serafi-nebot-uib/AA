package com.serafinebot.p7;

import com.serafinebot.p7.controller.SimulationController;
import com.serafinebot.p7.view.SimulationFrame;

import java.awt.EventQueue;

/**
 * Swing entry point for the optional graphical interface.
 *
 * <p>The GUI uses the same model classes as the console program but adds controls
 * for rule variants, square-frequency visualization, variant comparison and
 * parallel execution. Swing components are created on the event dispatch thread,
 * as required by Swing's threading model.</p>
 */
public final class GUI {

    private GUI() {}

    /**
     * Creates the view, wires the MVC controller and shows the window.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            SimulationFrame view = new SimulationFrame();
            new SimulationController(view);
            view.setVisible(true);
        });
    }
}
