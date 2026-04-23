package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.CompressionMode;

/**
 * Chooses which analyzed plan should actually be written.
 *
 * <p>The compressor always has STORED as a valid fallback. Automatic mode compares estimated archive
 * sizes, while explicit user requests select the requested archive format when its plan is valid.</p>
 */
final class HuffmanModeSelector {

    private HuffmanModeSelector() {
    }

    static CompressionMode select(CompressionMode requestedMode,
                                  long storedArchiveSize,
                                  WholePlan bytePlan,
                                  WholePlan wordPlan,
                                  BlockPlan blockPlan) {
        CompressionMode mode = requestedMode == null ? CompressionMode.AUTO : requestedMode;
        return switch (mode) {
            case AUTO -> selectBestMode(storedArchiveSize, bytePlan, wordPlan, blockPlan);
            case STORED -> CompressionMode.STORED;
            case HUFFMAN_1_BYTE -> bytePlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_1_BYTE;
            case HUFFMAN_2_BYTE -> wordPlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_2_BYTE;
            case HUFFMAN_BLOCK, HUFFMAN_BLOCK_1_BYTE, HUFFMAN_BLOCK_2_BYTE ->
                blockPlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_BLOCK;
        };
    }

    private static CompressionMode selectBestMode(long storedArchiveSize, WholePlan bytePlan, WholePlan wordPlan, BlockPlan blockPlan) {
        CompressionMode selected = CompressionMode.STORED;
        long bestSize = storedArchiveSize;

        if (bytePlan != null && bytePlan.estimatedArchiveSize() < bestSize) {
            selected = CompressionMode.HUFFMAN_1_BYTE;
            bestSize = bytePlan.estimatedArchiveSize();
        }
        if (wordPlan != null && wordPlan.estimatedArchiveSize() < bestSize) {
            selected = CompressionMode.HUFFMAN_2_BYTE;
            bestSize = wordPlan.estimatedArchiveSize();
        }
        if (blockPlan != null && blockPlan.estimatedArchiveSize() < bestSize) {
            selected = CompressionMode.HUFFMAN_BLOCK;
        }
        return selected;
    }
}
