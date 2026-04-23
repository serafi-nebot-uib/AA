package com.serafinebot.p4.model.report;

import com.serafinebot.p4.model.archive.CompressionMode;

/**
 * Statistics collected after decompressing a file.
 *
 * @param mode effective mode stored in the archive
 * @param archiveSize archive size in bytes
 * @param restoredSize restored output size in bytes
 * @param elapsedMillis wall-clock decompression time in milliseconds
 * @param treeBuildMillis cumulative time spent building Huffman trees in milliseconds
 */
public record DecompressionStats(
    CompressionMode mode,
    long archiveSize,
    long restoredSize,
    long elapsedMillis,
    long treeBuildMillis
) {
}
