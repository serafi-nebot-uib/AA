package com.serafinebot.p4;

import com.serafinebot.p4.controller.Controller;
import com.serafinebot.p4.view.MainWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * GUI entry point for the P4 application.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installSystemLookAndFeel();
            MainWindow view = new MainWindow();
            new Controller(view);
            view.showWindow();
        });
    }

    private static void installSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Falling back to the default look and feel is acceptable.
        }
    }
}
