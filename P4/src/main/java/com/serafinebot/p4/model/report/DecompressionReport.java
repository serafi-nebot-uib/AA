package com.serafinebot.p4.model.report;

import java.util.List;

/**
 * Rich decompression report used by the GUI.
 *
 * @param result raw decompression statistics
 * @param symbols symbol frequencies and assigned codes reconstructed from the archive, or an empty
 *     list for stored archives
 * @param tree reconstructed Huffman tree, or {@code null} for stored archives
 * @param blocks per-block reports (populated for block-mode archives, empty otherwise)
 */
public record DecompressionReport(
    DecompressionStats result,
    List<HuffmanSymbolInfo> symbols,
    HuffmanTreeNodeInfo tree,
    List<BlockReport> blocks
) {
    public DecompressionReport {
        symbols = List.copyOf(symbols);
        blocks = List.copyOf(blocks);
    }

    public DecompressionReport(DecompressionStats result, List<HuffmanSymbolInfo> symbols, HuffmanTreeNodeInfo tree) {
        this(result, symbols, tree, List.of());
    }
}
