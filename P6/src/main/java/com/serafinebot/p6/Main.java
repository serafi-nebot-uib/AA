package com.serafinebot.p6;

import com.serafinebot.p6.controller.GameController;
import com.serafinebot.p6.view.GameFrame;

import java.awt.EventQueue;

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
