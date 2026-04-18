package com.serafinebot.p4.model.report;

import java.util.List;

/**
 * Rich compression report used by the GUI.
 *
 * @param result raw compression statistics
 * @param symbols symbol frequencies and assigned codes, possibly empty for empty input
 * @param tree generated Huffman tree, or {@code null} for empty input
 */
public record CompressionReport(
    CompressionResult result,
    List<HuffmanSymbolInfo> symbols,
    HuffmanTreeNodeInfo tree
) {
    public CompressionReport {
        symbols = List.copyOf(symbols);
    }
}
