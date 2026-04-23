package com.serafinebot.p4.model.info;

import java.util.List;

/**
 * Rich compression information used by the GUI.
 *
 * @param stats raw compression statistics
 * @param symbols symbol frequencies and assigned codes, possibly empty for empty input
 * @param tree generated Huffman tree, or {@code null} for empty input
 * @param blocks per-block information (possibly one entry for single-tree modes, multiple for block mode)
 */
public record CompressionInfo(
    CompressionStats stats,
    List<HuffmanSymbolInfo> symbols,
    HuffmanTreeNodeInfo tree,
    List<BlockInfo> blocks
) {
    public CompressionInfo {
        symbols = List.copyOf(symbols);
        blocks = List.copyOf(blocks);
    }

    public CompressionInfo(CompressionStats stats, List<HuffmanSymbolInfo> symbols, HuffmanTreeNodeInfo tree) {
        this(stats, symbols, tree, List.of());
    }
}
