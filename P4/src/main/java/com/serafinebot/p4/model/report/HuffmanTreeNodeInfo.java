package com.serafinebot.p4.model.report;

/**
 * Immutable view-model representation of a Huffman tree node.
 *
 * @param symbol byte value for leaves, or minimum subtree symbol for internal nodes
 * @param frequency combined frequency represented by this node
 * @param probability node probability in the range {@code 0.0..1.0}
 * @param code prefix code from the root to this node; the root stores the empty string
 * @param leaf whether the node is a leaf
 * @param zeroChild child reached by bit {@code 0}, or {@code null} for a leaf
 * @param oneChild child reached by bit {@code 1}, or {@code null} for a leaf
 */
public record HuffmanTreeNodeInfo(
    int symbol,
    long frequency,
    double probability,
    String code,
    boolean leaf,
    HuffmanTreeNodeInfo zeroChild,
    HuffmanTreeNodeInfo oneChild
) {
}
