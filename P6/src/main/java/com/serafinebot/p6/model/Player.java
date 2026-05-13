package com.serafinebot.p6.model;

public enum Player {
    RED,
    YELLOW;

    public Player opponent() {
        return this == RED ? YELLOW : RED;
    }
}
