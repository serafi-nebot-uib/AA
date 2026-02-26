package com.serafinebot.p1;

import com.serafinebot.p1.controller.Controller;
import com.serafinebot.p1.model.Model;
import com.serafinebot.p1.view.MainView;

import javax.swing.*;

/**
 * Punt d'entrada de l'aplicacio.
 * Crea el model, la vista i el controlador seguint el patro MVC.
 */
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
