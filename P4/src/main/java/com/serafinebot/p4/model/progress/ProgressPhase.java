package com.serafinebot.p4.model.progress;

/**
 * Current phase of the compression workflow.
 */
public enum ProgressPhase {
    /**
     * Input is being scanned to build the frequency table.
     */
    ANALYZING,
    /**
     * Archive output is being written.
     */
    COMPRESSING,
    /**
     * Archive payload is being decoded back to raw bytes.
     */
    DECOMPRESSING
}
