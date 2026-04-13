package com.serafinebot.p3;

import com.serafinebot.p3.controller.Controller;
import com.serafinebot.p3.model.Model;
import com.serafinebot.p3.view.MainView;

import javax.swing.*;

/** Application entry point. Builds the MVC triad on the Swing EDT. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Model model = new Model();
            MainView view = new MainView(model);
            new Controller(model, view);
            view.setVisible(true);
        });
    }
}
