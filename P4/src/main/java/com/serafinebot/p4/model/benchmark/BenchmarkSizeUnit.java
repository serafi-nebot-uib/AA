package com.serafinebot.p4.model.benchmark;

/**
 * Unit used by the benchmark size controls.
 */
public enum BenchmarkSizeUnit {
    BYTES("Bytes", 1L),
    KIB("KiB", 1024L),
    MIB("MiB", 1024L * 1024L),
    GIB("GiB", 1024L * 1024L * 1024L);

    private final String displayName;
    private final long multiplier;

    BenchmarkSizeUnit(String displayName, long multiplier) {
        this.displayName = displayName;
        this.multiplier = multiplier;
    }

    public long multiplier() {
        return multiplier;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
