package com.serafinebot.p6.view;

public enum AgentType {
    RANDOM("Random"),
    GREEDY("Greedy"),
    MINIMAX("Minimax");

    private final String label;

    AgentType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
