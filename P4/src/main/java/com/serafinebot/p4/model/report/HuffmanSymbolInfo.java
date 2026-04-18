package com.serafinebot.p4.model.report;

/**
 * Symbol information derived from a Huffman tree.
 *
 * @param symbol byte value in the range {@code 0..255}
 * @param frequency frequency of the symbol in the analyzed file
 * @param probability symbol probability in the range {@code 0.0..1.0}
 * @param code Huffman code expressed as a string of {@code 0} and {@code 1}
 */
public record HuffmanSymbolInfo(
    int symbol,
    long frequency,
    double probability,
    String code
) {
}
