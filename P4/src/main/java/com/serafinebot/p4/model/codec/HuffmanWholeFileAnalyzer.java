package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.progress.ProgressListener;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressTracker;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds plans for Huffman modes that use one global tree for the whole file.
 */
final class HuffmanWholeFileAnalyzer {

    private static final int BUFFER_SIZE = 8192;
    private static final int WORD_SYMBOL_SPACE = 65536;

    private final PriorityQueueStrategy priorityQueueStrategy;
    private long treeBuildNanos;

    HuffmanWholeFileAnalyzer(PriorityQueueStrategy priorityQueueStrategy) {
        this.priorityQueueStrategy = priorityQueueStrategy;
    }

    WholePlan analyzeByte(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        FrequencyTable table = analyzeByteFrequencies(inputPath, totalBytes, listener);
        HuffmanCode root = buildTree(table);
        HuffmanCode[] leaves = HuffmanCode.lookupBySymbol(root, table.symbolSpaceSize());
        long bitCount = table.encodedBitCount(leaves);
        double entropy = table.entropy();
        double averageCodeLength = table.averageCodeLength(bitCount);
        ArchiveHeader header = ArchiveHeader.huffman1Byte(totalBytes, table.copyFrequencies());
        long estimatedArchiveSize = header.sizeInBytes() + (long) Math.ceil(bitCount / (double) Byte.SIZE);
        return new WholePlan(
            table,
            root,
            leaves,
            bitCount,
            entropy,
            averageCodeLength,
            header,
            header.sizeInBytes(),
            estimatedArchiveSize
        );
    }

    WholePlan analyzeWord(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        if (totalBytes < 2L) {
            return null;
        }

        FrequencyTable table = analyzeWordFrequencies(inputPath, totalBytes, listener);
        if (table.totalCount() == 0L) {
            return null;
        }

        HuffmanCode root = buildTree(table);
        HuffmanCode[] leaves = HuffmanCode.lookupBySymbol(root, table.symbolSpaceSize());
        long bitCount = table.encodedBitCount(leaves);
        double entropy = table.entropy();
        double averageCodeLength = table.averageCodeLength(bitCount);
        ArchiveHeader header = ArchiveHeader.huffman2Byte(totalBytes, table.copyFrequencies());
        long trailingByte = totalBytes % 2L == 0L ? 0L : 1L;
        long estimatedArchiveSize = header.sizeInBytes() + (long) Math.ceil(bitCount / (double) Byte.SIZE) + trailingByte;
        return new WholePlan(
            table,
            root,
            leaves,
            bitCount,
            entropy,
            averageCodeLength,
            header,
            header.sizeInBytes(),
            estimatedArchiveSize
        );
    }

    private FrequencyTable analyzeByteFrequencies(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        FrequencyTable table = new FrequencyTable();
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.ANALYZING, totalBytes);

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long processedBytes = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                table.add(buffer, read);
                processedBytes += read;
                tracker.update(processedBytes);
            }
            tracker.complete(processedBytes);
        }

        return table;
    }

    private FrequencyTable analyzeWordFrequencies(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        FrequencyTable table = new FrequencyTable(WORD_SYMBOL_SPACE);
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.ANALYZING, totalBytes);
        int pendingByte = -1;

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long processedBytes = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                for (int i = 0; i < read; i++) {
                    int value = buffer[i] & 0xFF;
                    if (pendingByte < 0) {
                        pendingByte = value;
                    } else {
                        table.addSymbol((pendingByte << 8) | value);
                        pendingByte = -1;
                    }
                }
                processedBytes += read;
                tracker.update(processedBytes);
            }
            tracker.complete(processedBytes);
        }

        return table;
    }

    long treeBuildNanos() {
        return treeBuildNanos;
    }

    private HuffmanCode buildTree(FrequencyTable table) {
        long t0 = System.nanoTime();
        HuffmanCode root = HuffmanCode.buildFromFrequencies(
            table,
            priorityQueueStrategy.createQueue()
        );
        treeBuildNanos += System.nanoTime() - t0;
        return root;
    }
}
