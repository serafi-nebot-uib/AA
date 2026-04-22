package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.report.BlockReport;
import com.serafinebot.p4.model.report.HuffmanSymbolInfo;
import com.serafinebot.p4.model.report.HuffmanTreeNodeInfo;

import java.util.List;

record BytePlan(FrequencyTable table,
                HuffmanCode root,
                HuffmanCode[] leaves,
                long bitCount,
                double entropy,
                double averageCodeLength,
                ArchiveHeader header,
                long metadataOverhead,
                long estimatedArchiveSize) {
}

record WordPlan(FrequencyTable table,
                HuffmanCode root,
                HuffmanCode[] leaves,
                long bitCount,
                double entropy,
                double averageCodeLength,
                ArchiveHeader header,
                long metadataOverhead,
                long estimatedArchiveSize) {
}

record BlockUnit(int blockSize,
                 CompressionMode mode,
                 FrequencyTable byteTable,
                 HuffmanCode byteRoot,
                 HuffmanCode[] byteLeaves,
                 FrequencyTable wordTable,
                 HuffmanCode wordRoot,
                 HuffmanCode[] wordLeaves) {
}

record BlockPlan(int blockSize,
                 List<BlockUnit> blocks,
                 ArchiveHeader header,
                 long metadataOverhead,
                 long estimatedArchiveSize,
                 long totalCompressedBits,
                 double entropy,
                 double averageCodeLength) {
}

record BlockDecodeResult(long bytesWritten, HuffmanTreeNodeInfo tree, List<HuffmanSymbolInfo> symbols) {
}

record DecodedArchive(CompressionMode mode,
                      long originalSize,
                      List<HuffmanSymbolInfo> symbols,
                      HuffmanTreeNodeInfo tree,
                      List<BlockReport> blocks) {
}
