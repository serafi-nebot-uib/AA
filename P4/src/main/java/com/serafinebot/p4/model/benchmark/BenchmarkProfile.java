package com.serafinebot.p4.model.benchmark;

/**
 * Synthetic dataset profile used during benchmark execution.
 */
public enum BenchmarkProfile {
    TEXT_LIKE("Text semblant"),
    LOW_ENTROPY("Baixa entropia"),
    RANDOM_BYTES("Aleatori");

    private final String displayName;

    BenchmarkProfile(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
