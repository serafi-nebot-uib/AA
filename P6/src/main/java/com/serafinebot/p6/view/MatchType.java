package com.serafinebot.p6.view;

public enum MatchType {
    HUMAN_VS_HUMAN("Huma vs Huma"),
    HUMAN_VS_AGENT("Huma vs Agent"),
    AGENT_VS_AGENT("Agent vs Agent");

    private final String label;

    MatchType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
