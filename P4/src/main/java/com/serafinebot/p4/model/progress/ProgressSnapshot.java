package com.serafinebot.p4.model.progress;

/**
 * Progress information suitable for later controller/view integration.
 *
 * @param phase current phase of the operation
 * @param processedBytes bytes already processed in the current phase
 * @param totalBytes total number of bytes expected in the current phase
 * @param elapsedMillis elapsed time in milliseconds for the current phase
 * @param estimatedRemainingMillis remaining-time estimate in milliseconds, or {@code -1} when the
 *     estimate is not yet meaningful
 */
public record ProgressSnapshot(
    ProgressPhase phase,
    long processedBytes,
    long totalBytes,
    long elapsedMillis,
    long estimatedRemainingMillis
) {
    /**
     * Returns completion as a value between {@code 0.0} and {@code 1.0}.
     */
    public double completion() {
        if (totalBytes == 0L) {
            return 1.0;
        }
        return Math.min(1.0, processedBytes / (double) totalBytes);
    }
}
