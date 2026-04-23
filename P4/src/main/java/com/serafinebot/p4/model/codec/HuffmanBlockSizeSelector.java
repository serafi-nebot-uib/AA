package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Estimates a good block size for block-mode Huffman compression.
 */
final class HuffmanBlockSizeSelector {

    private static final int BUFFER_SIZE = 8192;
    private static final int BYTE_SYMBOL_SPACE = 256;
    private static final int WORD_SYMBOL_SPACE = 65536;
    private static final int BLOCK_HEADER_SIZE = Integer.BYTES + Byte.BYTES;
    private static final int BLOCK_BYTE_FREQUENCY_ENTRY_SIZE = Byte.BYTES + Long.BYTES;
    private static final int BLOCK_WORD_FREQUENCY_ENTRY_SIZE = Short.BYTES + Long.BYTES;
    private static final int ANALYSIS_BLOCK_SIZE = 4096;
    private static final int[] CANDIDATE_BLOCK_SIZES = {4096, 16384, 65536, 262144, 1048576, 4194304, 16777216};

    private HuffmanBlockSizeSelector() {
    }

    static int select(Path inputPath, long totalBytes, boolean allowByteMode, boolean allowWordMode) throws IOException {
        // Try only a small set of practical block sizes. Running a full Huffman compression for
        // every candidate would be expensive, so this method estimates the archive size instead.
        // The estimate uses Shannon's ideal bit count from observed frequencies; it is not exact,
        // but it is good enough to choose between "small blocks adapt better" and "large blocks pay
        // less metadata overhead".
        int numCandidates = CANDIDATE_BLOCK_SIZES.length;

        // Each candidate is simulated in one pass over the file. Every candidate owns the frequency
        // tables for its current synthetic block. For example, the 4 KiB candidate flushes after
        // every sample, while the 64 KiB candidate accumulates sixteen samples before estimating one
        // larger block.
        FrequencyTable[] byteTables = createTables(numCandidates, BYTE_SYMBOL_SPACE);
        FrequencyTable[] wordTables = createTables(numCandidates, WORD_SYMBOL_SPACE);
        int[] bytes = new int[numCandidates];
        long[] totalEstimates = new long[numCandidates];

        long headerSize = ArchiveHeader.block(totalBytes).sizeInBytes();
        Arrays.fill(totalEstimates, headerSize);

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[ANALYSIS_BLOCK_SIZE];
            int read;
            while ((read = input.readNBytes(buffer, 0, ANALYSIS_BLOCK_SIZE)) > 0) {
                // The file is read as fixed 4 KiB analysis samples. Those samples can be
                // combined to simulate every larger candidate block size without rereading the file.
                for (int c = 0; c < numCandidates; c++) {
                    // Feed the same sample into every candidate's synthetic current block.
                    byteTables[c].add(buffer, read);
                    if (allowWordMode) {
                        addWordSymbols(wordTables[c], buffer, read);
                    }
                    bytes[c] += read;

                    if (bytes[c] >= CANDIDATE_BLOCK_SIZES[c]) {
                        // Once a candidate has accumulated a full block, estimate the best local
                        // representation for that block: stored, byte Huffman, or word Huffman.
                        // This mirrors the real block compressor's choice without building trees.
                        totalEstimates[c] += estimateBlockCompressedSize(
                            byteTables[c],
                            wordTables[c],
                            bytes[c],
                            allowByteMode,
                            allowWordMode
                        );
                        byteTables[c].clear();
                        wordTables[c].clear();
                        bytes[c] = 0;
                    }
                }
            }
        }

        for (int c = 0; c < numCandidates; c++) {
            if (bytes[c] > 0) {
                // The last synthetic block for each candidate is usually partial. It still
                // contributes to the archive, so include it before comparing candidates.
                totalEstimates[c] += estimateBlockCompressedSize(
                    byteTables[c],
                    wordTables[c],
                    bytes[c],
                    allowByteMode,
                    allowWordMode
                );
            }
        }

        int bestIndex = 0;
        for (int c = 1; c < numCandidates; c++) {
            if (totalEstimates[c] < totalEstimates[bestIndex]) {
                bestIndex = c;
            }
        }

        // The selected size is the candidate with the smallest estimated complete archive size,
        // including the common block archive header and all estimated per-block payloads.
        return CANDIDATE_BLOCK_SIZES[bestIndex];
    }

    private static FrequencyTable[] createTables(int count, int symbolSpaceSize) {
        FrequencyTable[] tables = new FrequencyTable[count];
        for (int i = 0; i < tables.length; i++) {
            tables[i] = new FrequencyTable(symbolSpaceSize);
        }
        return tables;
    }

    private static void addWordSymbols(FrequencyTable table, byte[] buffer, int length) {
        int pairEnd = length - (length & 1);
        for (int i = 0; i < pairEnd; i += 2) {
            int word = ((buffer[i] & 0xFF) << 8) | (buffer[i + 1] & 0xFF);
            table.addSymbol(word);
        }
    }

    private static long estimateBlockCompressedSize(FrequencyTable byteTable,
                                                    FrequencyTable wordTable,
                                                    int blockBytes,
                                                    boolean allowByteMode,
                                                    boolean allowWordMode) {
        long best = (long) BLOCK_HEADER_SIZE + blockBytes;

        if (allowByteMode) {
            best = Math.min(best, estimateByteHuffmanBlockSize(byteTable));
        }
        if (allowWordMode) {
            best = Math.min(best, estimateWordHuffmanBlockSize(wordTable, blockBytes));
        }
        return best;
    }

    private static long estimateByteHuffmanBlockSize(FrequencyTable table) {
        int distinctSymbols = table.distinctSymbolCount();
        long metadata = (long) BLOCK_HEADER_SIZE + Integer.BYTES + (long) distinctSymbols * BLOCK_BYTE_FREQUENCY_ENTRY_SIZE;
        if (distinctSymbols <= 1) {
            return metadata;
        }

        double totalBits = table.entropy() * table.totalCount();
        return metadata + (long) Math.ceil(totalBits / Byte.SIZE);
    }

    private static long estimateWordHuffmanBlockSize(FrequencyTable table, int blockBytes) {
        if (blockBytes < 2) {
            return Long.MAX_VALUE;
        }

        int distinctSymbols = table.distinctSymbolCount();
        long trailing = (blockBytes & 1) == 1 ? 1L : 0L;
        long metadata = (long) BLOCK_HEADER_SIZE + Integer.BYTES + (long) distinctSymbols * BLOCK_WORD_FREQUENCY_ENTRY_SIZE;
        if (distinctSymbols <= 1) {
            return metadata + trailing;
        }

        double totalBits = table.entropy() * table.totalCount();
        return metadata + (long) Math.ceil(totalBits / Byte.SIZE) + trailing;
    }
}
