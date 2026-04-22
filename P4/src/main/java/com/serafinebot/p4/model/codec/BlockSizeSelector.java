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
final class BlockSizeSelector {

    private static final int BUFFER_SIZE = 8192;
    private static final int BYTE_SYMBOL_SPACE = 256;
    private static final int WORD_SYMBOL_SPACE = 65536;
    private static final int BLOCK_HEADER_SIZE = Integer.BYTES + Byte.BYTES;
    private static final int BLOCK_BYTE_FREQUENCY_ENTRY_SIZE = Byte.BYTES + Long.BYTES;
    private static final int BLOCK_WORD_FREQUENCY_ENTRY_SIZE = Short.BYTES + Long.BYTES;
    private static final int ANALYSIS_BLOCK_SIZE = 4096;
    private static final int[] CANDIDATE_BLOCK_SIZES = {4096, 16384, 65536, 262144, 1048576, 4194304, 16777216};

    private BlockSizeSelector() {
    }

    static int select(Path inputPath, long totalBytes, boolean allowByteMode, boolean allowWordMode) throws IOException {
        // Evaluate practical block sizes with 4 KiB samples. This avoids a full compression trial
        // per candidate while still reflecting local symbol distributions.
        int numCandidates = CANDIDATE_BLOCK_SIZES.length;
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
                Arrays.fill(miniByteFreqs, 0L);
                for (int i = 0; i < read; i++) {
                    miniByteFreqs[buffer[i] & 0xFF]++;
                }

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

                for (int v = 0; v < miniWordCount; v++) {
                    miniWordFreqs[miniWordVisited[v]] = 0L;
                }
            }
        }

        for (int c = 0; c < numCandidates; c++) {
            if (runningBytes[c] > 0) {
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
        return CANDIDATE_BLOCK_SIZES[bestIndex];
    }

    private static long estimateBlockCompressedSize(long[] byteFreqs,
                                                    long[] wordFreqs,
                                                    int[] wordVisited,
                                                    int wordVisitedCount,
                                                    int blockBytes,
                                                    boolean allowByteMode,
                                                    boolean allowWordMode) {
        long storedSize = (long) BLOCK_HEADER_SIZE + blockBytes;
        long best = storedSize;

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
        return metadata + bytesForBits((long) Math.ceil(totalBits));
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

        return metadata + bytesForBits((long) Math.ceil(totalBits)) + trailing;
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

    private static long bytesForBits(long bitCount) {
        return (bitCount + 7L) / 8L;
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
