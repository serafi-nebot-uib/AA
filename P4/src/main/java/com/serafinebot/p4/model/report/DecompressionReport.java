package com.serafinebot.p4.model.report;

import java.util.List;

/**
 * Rich decompression report used by the GUI.
 *
 * @param result raw decompression statistics
 * @param symbols symbol frequencies and assigned codes reconstructed from the archive, or an empty
 *     list for stored archives
 * @param tree reconstructed Huffman tree, or {@code null} for stored archives
 */
public record DecompressionReport(
    DecompressionResult result,
    List<HuffmanSymbolInfo> symbols,
    HuffmanTreeNodeInfo tree
) {
    public DecompressionReport {
        symbols = List.copyOf(symbols);
    }
}
