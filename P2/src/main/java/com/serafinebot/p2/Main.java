package com.serafinebot.p2;

import com.serafinebot.p2.controller.SolverController;
import com.serafinebot.p2.view.MainFrame;

import javax.swing.SwingUtilities;

/**
 * Application entry point.
 * Launches the Hamiltonian path solver GUI on the Event Dispatch Thread.
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            new SolverController(frame);  // wires model ↔ view
            frame.setVisible(true);
        });
    }
}
