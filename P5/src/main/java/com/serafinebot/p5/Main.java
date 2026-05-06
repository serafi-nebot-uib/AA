package com.serafinebot.p5;

import com.serafinebot.p5.controller.GameController;
import com.serafinebot.p5.view.GameFrame;

import java.awt.EventQueue;

/**
 * Application entry point. Run with
 * {@code mvn exec:java -Dexec.mainClass="com.serafinebot.p5.Main"}.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            GameFrame view = new GameFrame();
            new GameController(view);
            view.setVisible(true);
        });
    }
}
