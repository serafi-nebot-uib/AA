package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.progress.ProgressListener;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressTracker;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the plan for block Huffman compression.
 *
 * <p>Block mode is different from whole-file mode because every block makes its own local choice:
 * store the bytes directly, use a byte Huffman tree, or use a two-byte Huffman tree. This analyzer
 * owns that per-block decision so the public codec does not need to know the details.</p>
 */
final class HuffmanBlockAnalyzer extends HuffmanAnalyzer {

    private static final int BUFFER_SIZE = 8192;
    private static final int BYTE_SYMBOL_SPACE = 256;
    private static final int WORD_SYMBOL_SPACE = 65536;
    private static final int BLOCK_HEADER_SIZE = Integer.BYTES + Byte.BYTES;
    private static final int BLOCK_BYTE_FREQUENCY_ENTRY_SIZE = Byte.BYTES + Long.BYTES;
    private static final int BLOCK_WORD_FREQUENCY_ENTRY_SIZE = Short.BYTES + Long.BYTES;
    private static final int ANALYSIS_BLOCK_SIZE = 4096;

    private final boolean allowByteMode;
    private final boolean allowWordMode;

    HuffmanBlockAnalyzer(PriorityQueueStrategy priorityQueueStrategy, Set<CompressionMode> allowedBlockHuffmanModes) {
        super(priorityQueueStrategy);
        this.allowByteMode = allowedBlockHuffmanModes.contains(CompressionMode.HUFFMAN_1_BYTE);
        this.allowWordMode = allowedBlockHuffmanModes.contains(CompressionMode.HUFFMAN_2_BYTE);
    }

    BlockPlan analyze(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        // Empty files still produce a valid block archive header, but there are no block-local
        // decisions to make. Returning a complete empty plan here avoids running the block-size
        // heuristic on a file that cannot provide any frequency information.
        if (totalBytes == 0L) {
            ArchiveHeader header = ArchiveHeader.block(0L);
            return new BlockPlan(
                ANALYSIS_BLOCK_SIZE,
                List.of(),
                header,
                header.sizeInBytes(),
                header.sizeInBytes(),
                0L,
                0.0,
                0.0
            );
        }

        // Block mode has a real trade-off: small blocks adapt quickly when the input distribution
        // changes, while large blocks reduce the repeated frequency-table metadata. The selector
        // estimates that trade-off once, then the analyzer uses the chosen size for the real plan.
        int selectedBlockSize = HuffmanBlockSizeSelector.select(inputPath, totalBytes, allowByteMode, allowWordMode);

        ArchiveHeader header = ArchiveHeader.block(totalBytes);
        List<BlockUnit> blocks = new ArrayList<>();
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.ANALYZING, totalBytes);

        // These accumulators are built while scanning blocks so the final info can be produced without
        // re-reading the input. metadataOverhead counts only headers/frequency tables, while
        // estimatedArchiveSize also includes each block's estimated payload bytes.
        long processedBytes = 0L;
        long metadataOverhead = header.sizeInBytes();
        long estimatedArchiveSize = header.sizeInBytes();
        long totalCompressedBits = 0L;
        long totalEncodedSymbols = 0L;
        double weightedEntropy = 0.0;

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[selectedBlockSize];
            int read;
            while ((read = input.readNBytes(buffer, 0, selectedBlockSize)) > 0) {
                // Each block chooses independently between byte Huffman, word Huffman, and stored.
                // That local decision is what lets the compressor handle files whose distribution
                // changes across the file instead of forcing one global tree to fit every region.
                FrequencyTable byteTable = new FrequencyTable(BYTE_SYMBOL_SPACE);
                byteTable.add(buffer, read);

                // The byte table is always built, even when byte-Huffman blocks are disabled. It is
                // cheap, it is needed for entropy information, and it gives stored blocks meaningful
                // statistics in the GUI instead of leaving block analysis visually empty.
                HuffmanCode byteRoot = null;
                HuffmanCode[] byteLeaves = null;
                long byteBitCount = 0L;
                long byteMetadata = 0L;
                long byteSize = Long.MAX_VALUE;
                if (allowByteMode) {
                    byteRoot = buildTree(byteTable);
                    byteLeaves = HuffmanCode.lookupBySymbol(byteRoot, byteTable.symbolSpaceSize());
                    byteBitCount = byteTable.encodedBitCount(byteLeaves);
                    byteMetadata = BLOCK_HEADER_SIZE + Integer.BYTES + (long) byteTable.distinctSymbolCount() * BLOCK_BYTE_FREQUENCY_ENTRY_SIZE;
                    byteSize = byteMetadata + (long) Math.ceil(byteBitCount / (double) Byte.SIZE);
                }

                // The word candidate groups the block into 2-byte symbols. Odd-length blocks keep
                // the final byte outside the Huffman stream, so its raw byte must be included in the
                // estimated size. A block shorter than two bytes cannot use this mode.
                FrequencyTable wordTable = null;
                HuffmanCode wordRoot = null;
                HuffmanCode[] wordLeaves = null;
                long wordBitCount = 0L;
                long wordMetadata = 0L;
                long wordSize = Long.MAX_VALUE;
                if (allowWordMode && read >= 2) {
                    wordTable = buildBlockWordFrequencyTable(buffer, read);
                    if (wordTable.totalCount() > 0L) {
                        wordRoot = buildTree(wordTable);
                        wordLeaves = HuffmanCode.lookupBySymbol(wordRoot, wordTable.symbolSpaceSize());
                        wordBitCount = wordTable.encodedBitCount(wordLeaves);
                        wordMetadata = BLOCK_HEADER_SIZE + Integer.BYTES + (long) wordTable.distinctSymbolCount() * BLOCK_WORD_FREQUENCY_ENTRY_SIZE;
                        long trailing = (read & 1) == 1 ? 1L : 0L;
                        wordSize = wordMetadata + (long) Math.ceil(wordBitCount / (double) Byte.SIZE) + trailing;
                    }
                }

                long storedSize = BLOCK_HEADER_SIZE + read;

                // Stored is the local fallback for every block. A Huffman candidate must beat that
                // local stored size before it is chosen; this prevents block mode from wasting space
                // on frequency metadata when the block is too small or too uniform for that mode to
                // pay off. If byte and word Huffman tie, word mode wins because it was at least as
                // compact with fewer encoded symbols.
                CompressionMode blockMode;
                long blockMetadata;
                long blockEncodedSize;
                long blockEncodedBits;
                long blockEncodedSymbols;
                if (wordSize < storedSize && wordSize <= byteSize) {
                    blockMode = CompressionMode.HUFFMAN_2_BYTE;
                    blockMetadata = wordMetadata;
                    blockEncodedSize = wordSize;
                    blockEncodedBits = wordBitCount;
                    blockEncodedSymbols = wordTable.totalCount();
                } else if (byteSize < storedSize) {
                    blockMode = CompressionMode.HUFFMAN_1_BYTE;
                    blockMetadata = byteMetadata;
                    blockEncodedSize = byteSize;
                    blockEncodedBits = byteBitCount;
                    blockEncodedSymbols = byteTable.totalCount();
                } else {
                    blockMode = CompressionMode.STORED;
                    blockMetadata = BLOCK_HEADER_SIZE;
                    blockEncodedSize = storedSize;
                    blockEncodedBits = 0L;
                    blockEncodedSymbols = 0L;
                }

                // BlockUnit keeps the selected mode plus the analysis data the writer will need
                // later. The input is streamed again during writing, so storing the tree/leaves here
                // avoids rebuilding the same Huffman tree a second time.
                blocks.add(new BlockUnit(read, blockMode, byteTable, byteRoot, byteLeaves, wordTable, wordRoot, wordLeaves));
                metadataOverhead += blockMetadata;
                estimatedArchiveSize += blockEncodedSize;

                // Only Huffman blocks contribute encoded-bit and average-code-length statistics.
                // Stored blocks copy bytes directly, so counting them as zero-bit Huffman codes
                // would distort the average.
                if (blockMode != CompressionMode.STORED) {
                    totalCompressedBits += blockEncodedBits;
                    totalEncodedSymbols += blockEncodedSymbols;
                }

                // Entropy is weighted by bytes so that one tiny unusual block does not affect the
                // file-level block information as much as a large region of the input.
                weightedEntropy += byteTable.entropy() * read;

                processedBytes += read;
                tracker.update(processedBytes);
            }
        }

        tracker.complete(processedBytes);

        // The average code length is computed over the symbols that were actually Huffman-encoded.
        // If every block fell back to STORED, there is no Huffman code length to average.
        double averageCodeLength = totalEncodedSymbols == 0L ? 0.0 : (double) totalCompressedBits / totalEncodedSymbols;
        double entropy = weightedEntropy / totalBytes;
        return new BlockPlan(
            selectedBlockSize,
            List.copyOf(blocks),
            header,
            metadataOverhead,
            estimatedArchiveSize,
            totalCompressedBits,
            entropy,
            averageCodeLength
        );
    }

    private FrequencyTable buildBlockWordFrequencyTable(byte[] buffer, int length) {
        FrequencyTable table = new FrequencyTable(WORD_SYMBOL_SPACE);
        int pairEnd = length - (length & 1);
        for (int i = 0; i < pairEnd; i += 2) {
            int word = ((buffer[i] & 0xFF) << 8) | (buffer[i + 1] & 0xFF);
            table.addSymbol(word);
        }
        return table;
    }

}
