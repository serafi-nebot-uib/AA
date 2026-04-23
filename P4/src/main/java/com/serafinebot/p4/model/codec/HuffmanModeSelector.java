package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.CompressionMode;

/**
 * Chooses which analyzed plan should actually be written.
 *
 * <p>Whole-file STORED is intentionally not selected here. Stored payloads are only valid as a
 * block-local fallback; whole-file compression must either produce a Huffman archive or fail the
 * size check before anything is written.</p>
 */
final class HuffmanModeSelector {

    private HuffmanModeSelector() {
    }

    static HuffmanArchive select(CompressionMode requestedMode,
                                 WholePlan bytePlan,
                                 WholePlan wordPlan,
                                 BlockPlan blockPlan) {
        CompressionMode mode = requestedMode == null ? CompressionMode.AUTO : requestedMode;
        return switch (mode) {
            case AUTO -> selectBestMode(bytePlan, wordPlan, blockPlan);
            case STORED -> throw new IllegalArgumentException("El mode STORED nomes es pot usar dins blocs.");
            case HUFFMAN_1_BYTE -> HuffmanArchive.wholeFile(CompressionMode.HUFFMAN_1_BYTE, requirePlan(bytePlan, mode));
            case HUFFMAN_2_BYTE -> HuffmanArchive.wholeFile(CompressionMode.HUFFMAN_2_BYTE, requirePlan(wordPlan, mode));
            case HUFFMAN_BLOCK, HUFFMAN_BLOCK_1_BYTE, HUFFMAN_BLOCK_2_BYTE ->
                blockPlan == null ? unavailable(CompressionMode.HUFFMAN_BLOCK) : HuffmanArchive.block(blockPlan);
        };
    }

    private static HuffmanArchive selectBestMode(WholePlan bytePlan, WholePlan wordPlan, BlockPlan blockPlan) {
        HuffmanArchive selected = null;
        long bestSize = Long.MAX_VALUE;

        if (bytePlan != null && bytePlan.estimatedArchiveSize() < bestSize) {
            selected = HuffmanArchive.wholeFile(CompressionMode.HUFFMAN_1_BYTE, bytePlan);
            bestSize = bytePlan.estimatedArchiveSize();
        }
        if (wordPlan != null && wordPlan.estimatedArchiveSize() < bestSize) {
            selected = HuffmanArchive.wholeFile(CompressionMode.HUFFMAN_2_BYTE, wordPlan);
            bestSize = wordPlan.estimatedArchiveSize();
        }
        if (blockPlan != null && blockPlan.estimatedArchiveSize() < bestSize) {
            selected = HuffmanArchive.block(blockPlan);
        }
        return selected == null ? unavailable(CompressionMode.AUTO) : selected;
    }

    private static WholePlan requirePlan(WholePlan plan, CompressionMode mode) {
        return plan == null ? unavailable(mode) : plan;
    }

    private static <T> T unavailable(CompressionMode mode) {
        throw new IllegalArgumentException("No hi ha cap pla de compressio valid per al mode " + mode + '.');
    }
}
