package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import java.util.List;

/**
 * Analysis result for a whole-file Huffman mode.
 *
 * <p>One-byte and two-byte Huffman modes build frequencies differently and write their payloads
 * differently, but after analysis they both need this same bundle: tree, lookup table, entropy
 * metrics, archive header, and estimated output size.</p>
 */
record WholePlan(FrequencyTable table,
                 HuffmanCode root,
                 HuffmanCode[] leaves,
                 long bitCount,
                 double entropy,
                 double averageCodeLength,
                 ArchiveHeader header,
                 long metadataOverhead,
                 long estimatedArchiveSize) {
}

/**
 * Analysis result for a single block inside block mode.
 *
 * <p>Block compression compares the byte tree, the word tree, and the uncompressed representation
 * before it writes anything. This record keeps both candidate Huffman analyses because the selected
 * mode is only known after their estimated sizes have been compared.</p>
 */
record BlockUnit(int blockSize,
                 CompressionMode mode,
                 FrequencyTable byteTable,
                 HuffmanCode byteRoot,
                 HuffmanCode[] byteLeaves,
                 FrequencyTable wordTable,
                 HuffmanCode wordRoot,
                 HuffmanCode[] wordLeaves) {
}

/**
 * Complete plan for block mode.
 *
 * <p>The archive writer needs the per-block decisions, while the final compression info needs
 * aggregate values such as total encoded bits, weighted entropy, and metadata overhead. Keeping both
 * in one immutable value prevents the writer and info factory from recomputing analysis data.</p>
 */
record BlockPlan(int blockSize,
                 List<BlockUnit> blocks,
                 ArchiveHeader header,
                 long metadataOverhead,
                 long estimatedArchiveSize,
                 long totalCompressedBits,
                 double entropy,
                 double averageCodeLength) {
}
