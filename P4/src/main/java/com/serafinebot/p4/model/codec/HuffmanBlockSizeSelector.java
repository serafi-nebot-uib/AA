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

        // Each candidate is simulated in one pass over the file. The running arrays hold the
        // frequencies of the current synthetic block for that candidate. For example, the 4 KiB
        // candidate flushes after every sample, while the 64 KiB candidate accumulates sixteen
        // samples before estimating one larger block.
        long[][] runningByteFreqs = new long[numCandidates][BYTE_SYMBOL_SPACE];
        long[][] runningWordFreqs = new long[numCandidates][WORD_SYMBOL_SPACE];
        int[][] runningWordVisited = new int[numCandidates][WORD_SYMBOL_SPACE];
        int[] runningWordVisitedCount = new int[numCandidates];
        int[] runningBytes = new int[numCandidates];
        long[] totalEstimates = new long[numCandidates];

        long headerSize = ArchiveHeader.block(totalBytes).sizeInBytes();
        Arrays.fill(totalEstimates, headerSize);

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[ANALYSIS_BLOCK_SIZE];
            long[] miniByteFreqs = new long[BYTE_SYMBOL_SPACE];
            long[] miniWordFreqs = new long[WORD_SYMBOL_SPACE];
            int[] miniWordVisited = new int[ANALYSIS_BLOCK_SIZE / 2 + 1];
            int read;
            while ((read = readBlock(input, buffer, ANALYSIS_BLOCK_SIZE)) > 0) {
                // The file is read as fixed 4 KiB analysis samples. Those samples are small enough
                // to keep memory predictable, and they can be combined to simulate every larger
                // candidate block size without rereading the file.
                Arrays.fill(miniByteFreqs, 0L);
                for (int i = 0; i < read; i++) {
                    miniByteFreqs[buffer[i] & 0xFF]++;
                }

                // Word-mode frequencies live in a 65,536-symbol space. Tracking only the words
                // that appeared lets us reset and iterate sparse word frequencies cheaply.
                int miniWordCount = 0;
                int pairEnd = read - (read & 1);
                for (int i = 0; i < pairEnd; i += 2) {
                    int word = ((buffer[i] & 0xFF) << 8) | (buffer[i + 1] & 0xFF);
                    if (miniWordFreqs[word] == 0L) {
                        miniWordVisited[miniWordCount++] = word;
                    }
                    miniWordFreqs[word]++;
                }

                for (int c = 0; c < numCandidates; c++) {
                    // Feed the same sample into every candidate's synthetic current block.
                    for (int s = 0; s < BYTE_SYMBOL_SPACE; s++) {
                        runningByteFreqs[c][s] += miniByteFreqs[s];
                    }
                    for (int v = 0; v < miniWordCount; v++) {
                        int word = miniWordVisited[v];
                        if (runningWordFreqs[c][word] == 0L) {
                            runningWordVisited[c][runningWordVisitedCount[c]++] = word;
                        }
                        runningWordFreqs[c][word] += miniWordFreqs[word];
                    }
                    runningBytes[c] += read;

                    if (runningBytes[c] >= CANDIDATE_BLOCK_SIZES[c]) {
                        // Once a candidate has accumulated a full block, estimate the best local
                        // representation for that block: stored, byte Huffman, or word Huffman.
                        // This mirrors the real block compressor's choice without building trees.
                        totalEstimates[c] += estimateBlockCompressedSize(
                            runningByteFreqs[c],
                            runningWordFreqs[c],
                            runningWordVisited[c],
                            runningWordVisitedCount[c],
                            runningBytes[c],
                            allowByteMode,
                            allowWordMode
                        );
                        Arrays.fill(runningByteFreqs[c], 0L);
                        for (int v = 0; v < runningWordVisitedCount[c]; v++) {
                            runningWordFreqs[c][runningWordVisited[c][v]] = 0L;
                        }
                        runningWordVisitedCount[c] = 0;
                        runningBytes[c] = 0;
                    }
                }

                // Clear only the sparse word entries touched by this sample. Clearing the whole
                // 65,536-element array for every 4 KiB sample is avoidable work.
                for (int v = 0; v < miniWordCount; v++) {
                    miniWordFreqs[miniWordVisited[v]] = 0L;
                }
            }
        }

        for (int c = 0; c < numCandidates; c++) {
            if (runningBytes[c] > 0) {
                // The last synthetic block for each candidate is usually partial. It still
                // contributes to the archive, so include it before comparing candidates.
                totalEstimates[c] += estimateBlockCompressedSize(
                    runningByteFreqs[c],
                    runningWordFreqs[c],
                    runningWordVisited[c],
                    runningWordVisitedCount[c],
                    runningBytes[c],
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

    private static long estimateBlockCompressedSize(long[] byteFreqs,
                                                    long[] wordFreqs,
                                                    int[] wordVisited,
                                                    int wordVisitedCount,
                                                    int blockBytes,
                                                    boolean allowByteMode,
                                                    boolean allowWordMode) {
        long best = (long) BLOCK_HEADER_SIZE + blockBytes;

        if (allowByteMode) {
            best = Math.min(best, estimateByteHuffmanBlockSize(byteFreqs));
        }
        if (allowWordMode) {
            best = Math.min(best, estimateWordHuffmanBlockSize(wordFreqs, wordVisited, wordVisitedCount, blockBytes));
        }
        return best;
    }

    private static long estimateByteHuffmanBlockSize(long[] frequencies) {
        int distinctSymbols = 0;
        long totalSymbols = 0L;
        for (long frequency : frequencies) {
            if (frequency > 0L) {
                distinctSymbols++;
                totalSymbols += frequency;
            }
        }

        long metadata = (long) BLOCK_HEADER_SIZE + Integer.BYTES + (long) distinctSymbols * BLOCK_BYTE_FREQUENCY_ENTRY_SIZE;
        if (distinctSymbols <= 1) {
            return metadata;
        }

        double totalBits = shannonBits(frequencies, totalSymbols);
        return metadata + (long) Math.ceil(totalBits / Byte.SIZE);
    }

    private static long estimateWordHuffmanBlockSize(long[] wordFreqs, int[] visited, int visitedCount, int blockBytes) {
        if (blockBytes < 2) {
            return Long.MAX_VALUE;
        }

        long totalPairs = 0L;
        for (int v = 0; v < visitedCount; v++) {
            totalPairs += wordFreqs[visited[v]];
        }

        long trailing = (blockBytes & 1) == 1 ? 1L : 0L;
        long metadata = (long) BLOCK_HEADER_SIZE + Integer.BYTES + (long) visitedCount * BLOCK_WORD_FREQUENCY_ENTRY_SIZE;
        if (visitedCount <= 1) {
            return metadata + trailing;
        }

        double totalBits = 0.0;
        double logTotal = Math.log(totalPairs);
        for (int v = 0; v < visitedCount; v++) {
            long frequency = wordFreqs[visited[v]];
            totalBits += frequency * (logTotal - Math.log(frequency));
        }
        totalBits /= Math.log(2);

        return metadata + (long) Math.ceil(totalBits / Byte.SIZE) + trailing;
    }

    private static double shannonBits(long[] frequencies, long totalSymbols) {
        double totalBits = 0.0;
        double logTotal = Math.log(totalSymbols);
        for (long frequency : frequencies) {
            if (frequency > 0L) {
                totalBits += frequency * (logTotal - Math.log(frequency));
            }
        }
        return totalBits / Math.log(2);
    }

    private static int readBlock(InputStream input, byte[] buffer, int maxBytes) throws IOException {
        int totalRead = 0;
        while (totalRead < maxBytes) {
            int read = input.read(buffer, totalRead, maxBytes - totalRead);
            if (read < 0) {
                break;
            }
            totalRead += read;
        }
        return totalRead;
    }
}
