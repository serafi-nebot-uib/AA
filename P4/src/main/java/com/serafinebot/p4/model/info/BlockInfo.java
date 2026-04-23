package com.serafinebot.p4.model.info;

import com.serafinebot.p4.model.archive.CompressionMode;

import java.util.List;

/**
 * Per-block view-model information used to render individual Huffman trees.
 *
 * @param blockSize number of original bytes represented by the block
 * @param mode compression mode actually used for the block
 * @param tree Huffman tree for the block, or {@code null} if not applicable
 * @param symbols symbols, frequencies, and codes for the block, possibly empty
 */
public record BlockInfo(
    int blockSize,
    CompressionMode mode,
    HuffmanTreeNodeInfo tree,
    List<HuffmanSymbolInfo> symbols
) {
    public BlockInfo {
        symbols = List.copyOf(symbols);
    }
}
