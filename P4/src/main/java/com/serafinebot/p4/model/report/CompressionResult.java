package com.serafinebot.p4.model.report;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

/**
 * Statistics collected after compressing a file.
 *
 * @param mode effective mode used in the written archive
 * @param priorityQueueStrategy queue strategy used to build the Huffman tree
 * @param originalSize original input size in bytes
 * @param archiveSize final archive size in bytes
 * @param distinctSymbolCount number of distinct byte values present in the input
 * @param theoreticalHuffmanBitCount number of payload bits produced by the Huffman codes alone
 * @param entropy Shannon entropy of the input distribution in bits per symbol
 * @param averageHuffmanCodeLength average Huffman code length in bits per symbol
 * @param elapsedMillis wall-clock compression time in milliseconds
 */
public record CompressionResult(
    CompressionMode mode,
    PriorityQueueStrategy priorityQueueStrategy,
    long originalSize,
    long archiveSize,
    int distinctSymbolCount,
    long theoreticalHuffmanBitCount,
    double entropy,
    double averageHuffmanCodeLength,
    long elapsedMillis
) {
    private static final long FIXED_HEADER_SIZE = 15L;
    private static final long FREQUENCY_ENTRY_SIZE = 9L;

    /**
     * Returns the header size implied by the selected mode.
     */
    public long overheadSize() {
        if (mode == CompressionMode.HUFFMAN) {
            return FIXED_HEADER_SIZE + distinctSymbolCount * FREQUENCY_ENTRY_SIZE;
        }
        return FIXED_HEADER_SIZE;
    }

    /**
     * Returns the payload size excluding the archive header.
     */
    public long payloadSize() {
        return Math.max(0L, archiveSize - overheadSize());
    }

    /**
     * Returns the fractional compression gain, where positive means smaller than the original.
     */
    public double compressionRate() {
        if (originalSize == 0L) {
            return 0.0;
        }
        return 1.0 - (archiveSize / (double) originalSize);
    }

    /**
     * Returns the fractional compression gain as a percentage.
     */
    public double compressionPercentage() {
        return compressionRate() * 100.0;
    }
}
