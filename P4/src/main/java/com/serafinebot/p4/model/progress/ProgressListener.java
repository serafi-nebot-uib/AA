package com.serafinebot.p4.model.progress;

/**
 * Listener used by the model to report progress information.
 */
@FunctionalInterface
public interface ProgressListener {
    /**
     * Receives a new progress snapshot.
     */
    void onProgress(ProgressSnapshot snapshot);
}
