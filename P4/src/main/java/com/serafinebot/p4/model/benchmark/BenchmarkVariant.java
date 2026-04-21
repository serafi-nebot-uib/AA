package com.serafinebot.p4.model.benchmark;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

import java.util.Set;

/**
 * Configuration variants used in per-mode benchmarks.
 */
public enum BenchmarkVariant {
    HUFFMAN_1_BYTE("Huffman 1 byte", CompressionMode.HUFFMAN_1_BYTE, null),
    HUFFMAN_2_BYTE("Huffman 2 bytes", CompressionMode.HUFFMAN_2_BYTE, null),
    HUFFMAN_BLOCK_1_BYTE("Huffman per blocs 1 byte", CompressionMode.HUFFMAN_BLOCK, Set.of(CompressionMode.HUFFMAN_1_BYTE)),
    HUFFMAN_BLOCK_2_BYTE("Huffman per blocs 2 bytes", CompressionMode.HUFFMAN_BLOCK, Set.of(CompressionMode.HUFFMAN_2_BYTE));

    private final String displayName;
    private final CompressionMode preferredCompressionMode;
    private final Set<CompressionMode> allowedBlockHuffmanModes;

    BenchmarkVariant(String displayName,
                     CompressionMode preferredCompressionMode,
                     Set<CompressionMode> allowedBlockHuffmanModes) {
        this.displayName = displayName;
        this.preferredCompressionMode = preferredCompressionMode;
        this.allowedBlockHuffmanModes = allowedBlockHuffmanModes;
    }

    public HuffmanCodec createCodec(PriorityQueueStrategy strategy) {
        return new HuffmanCodec(strategy, preferredCompressionMode, allowedBlockHuffmanModes);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
