package com.serafinebot.p4.model;

/**
 * Statistics collected after decompressing a file.
 *
 * @param mode effective mode stored in the archive
 * @param archiveSize archive size in bytes
 * @param restoredSize restored output size in bytes
 * @param elapsedMillis wall-clock decompression time in milliseconds
 */
public record DecompressionResult(
    CompressionMode mode,
    long archiveSize,
    long restoredSize,
    long elapsedMillis
) {
}
