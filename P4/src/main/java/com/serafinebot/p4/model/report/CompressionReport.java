package com.serafinebot.p4.model.report;

import java.util.List;

/**
 * Rich compression report used by the GUI.
 *
 * @param result raw compression statistics
 * @param symbols symbol frequencies and assigned codes, possibly empty for empty input
 * @param tree generated Huffman tree, or {@code null} for empty input
 * @param blocks per-block reports (possibly one entry for single-tree modes, multiple for block mode)
 */
public record CompressionReport(
    CompressionStats result,
    List<HuffmanSymbolInfo> symbols,
    HuffmanTreeNodeInfo tree,
    List<BlockReport> blocks
) {
    public CompressionReport {
        symbols = List.copyOf(symbols);
        blocks = List.copyOf(blocks);
    }

    public CompressionReport(CompressionStats result, List<HuffmanSymbolInfo> symbols, HuffmanTreeNodeInfo tree) {
        this(result, symbols, tree, List.of());
    }
}
