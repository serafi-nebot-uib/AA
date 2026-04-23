package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.report.BlockReport;
import com.serafinebot.p4.model.report.HuffmanSymbolInfo;
import com.serafinebot.p4.model.report.HuffmanTreeNodeInfo;

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
 * <p>The archive writer needs the per-block decisions, while the final compression report needs
 * aggregate values such as total encoded bits, weighted entropy, and metadata overhead. Keeping both
 * in one immutable value prevents the writer and report builder from recomputing analysis data.</p>
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

/**
 * Result of decoding one Huffman-compressed block.
 *
 * <p>During decompression the reader writes bytes directly to the output stream, so it cannot infer
 * the block report later from an in-memory payload. This DTO returns only the information still
 * needed after streaming: bytes written and the tree/symbol data for the GUI report.</p>
 */
record BlockDecodeResult(long bytesWritten, HuffmanTreeNodeInfo tree, List<HuffmanSymbolInfo> symbols) {
}

/**
 * Reader-side summary used to build the public decompression report.
 *
 * <p>The archive reader owns the file format details, but {@link HuffmanCodec} owns timing and the
 * public report objects. This record is the narrow hand-off between those two responsibilities.</p>
 */
record DecodedArchive(CompressionMode mode,
                      long originalSize,
                      List<HuffmanSymbolInfo> symbols,
                      HuffmanTreeNodeInfo tree,
                      List<BlockReport> blocks) {
}
