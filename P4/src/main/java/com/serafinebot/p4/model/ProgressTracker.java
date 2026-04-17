package com.serafinebot.p4.model;

/**
 * Emits throttled progress updates with a remaining-time estimate.
 *
 * <p>This class is internal to the model. It converts low-level byte counters into reusable
 * progress snapshots without coupling the model to any specific UI.</p>
 */
final class ProgressTracker {

    private static final long REPORT_INTERVAL_BYTES = 64L * 1024L;

    private final ProgressListener listener;
    private final ProgressPhase phase;
    private final long totalBytes;
    private final long startNanos;

    private long lastReportedBytes;

    /**
     * Starts tracking one phase of work.
     */
    ProgressTracker(ProgressListener listener, ProgressPhase phase, long totalBytes) {
        this.listener = listener;
        this.phase = phase;
        this.totalBytes = totalBytes;
        this.startNanos = System.nanoTime();
        this.lastReportedBytes = Long.MIN_VALUE;
        report(0L, true);
    }

    /**
     * Reports intermediate progress.
     */
    void update(long processedBytes) {
        report(processedBytes, false);
    }

    /**
     * Forces a final progress report.
     */
    void complete(long processedBytes) {
        report(processedBytes, true);
    }

    private void report(long processedBytes, boolean force) {
        if (listener == null) {
            return;
        }
        if (!force && processedBytes - lastReportedBytes < REPORT_INTERVAL_BYTES) {
            return;
        }

        // The estimate is intentionally simple: extrapolate from the average throughput observed so
        // far. Returning -1 means "not enough information yet".
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        long estimatedRemainingMillis;
        if (totalBytes == 0L || processedBytes >= totalBytes) {
            estimatedRemainingMillis = 0L;
        } else if (processedBytes == 0L || elapsedMillis == 0L) {
            estimatedRemainingMillis = -1L;
        } else {
            long remainingBytes = totalBytes - processedBytes;
            estimatedRemainingMillis = Math.max(0L, elapsedMillis * remainingBytes / processedBytes);
        }

        listener.onProgress(new ProgressSnapshot(phase, processedBytes, totalBytes, elapsedMillis, estimatedRemainingMillis));
        lastReportedBytes = processedBytes;
    }
}
