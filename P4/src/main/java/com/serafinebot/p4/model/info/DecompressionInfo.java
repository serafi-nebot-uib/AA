package com.serafinebot.p4.model.info;

import java.util.List;

/**
 * Rich decompression information used by the GUI.
 *
 * @param stats raw decompression statistics
 * @param symbols symbol frequencies and assigned codes reconstructed from the archive, or an empty
 *     list for stored archives
 * @param tree reconstructed Huffman tree, or {@code null} for stored archives
 * @param blocks per-block information (populated for block-mode archives, empty otherwise)
 */
public record DecompressionInfo(
    DecompressionStats stats,
    List<HuffmanSymbolInfo> symbols,
    HuffmanTreeNodeInfo tree,
    List<BlockInfo> blocks
) {
    public DecompressionInfo {
        symbols = List.copyOf(symbols);
        blocks = List.copyOf(blocks);
    }

    public DecompressionInfo(DecompressionStats stats, List<HuffmanSymbolInfo> symbols, HuffmanTreeNodeInfo tree) {
        this(stats, symbols, tree, List.of());
    }
}
