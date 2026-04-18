package com.serafinebot.p4.model.benchmark;

/**
 * Listener for benchmark progress updates.
 */
@FunctionalInterface
public interface BenchmarkProgressListener {
    void onProgress(BenchmarkProgressSnapshot snapshot);
}
