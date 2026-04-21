package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveFormatException;
import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.progress.ProgressListener;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressTracker;
import com.serafinebot.p4.model.queue.BinaryHeapNodeQueue;
import com.serafinebot.p4.model.queue.DichotomicListNodeQueue;
import com.serafinebot.p4.model.queue.FibonacciHeapNodeQueue;
import com.serafinebot.p4.model.queue.NodeQueue;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.CompressionReport;
import com.serafinebot.p4.model.report.CompressionResult;
import com.serafinebot.p4.model.report.DecompressionReport;
import com.serafinebot.p4.model.report.DecompressionResult;
import com.serafinebot.p4.model.report.HuffmanSymbolInfo;
import com.serafinebot.p4.model.report.HuffmanTreeNodeInfo;
import com.serafinebot.p4.util.BitInputStream;
import com.serafinebot.p4.util.BitOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * High-level model service for compressing and decompressing files with several Huffman strategies.
 */
public class HuffmanCodec {

    private static final int BUFFER_SIZE = 8192;
    private static final int WORD_SYMBOL_SPACE = 65536;
    private static final int BLOCK_HEADER_SIZE = Integer.BYTES + Byte.BYTES;
    private static final int BLOCK_BYTE_FREQUENCY_ENTRY_SIZE = Byte.BYTES + Long.BYTES;
    private static final int BLOCK_WORD_FREQUENCY_ENTRY_SIZE = Short.BYTES + Long.BYTES;
    private static final int ANALYSIS_BLOCK_SIZE = 4096;
    private static final int[] CANDIDATE_BLOCK_SIZES = {4096, 16384, 65536, 262144, 1048576, 4194304, 16777216};

    private static final java.util.Set<CompressionMode> DEFAULT_BLOCK_SUB_MODES =
        java.util.Set.of(CompressionMode.HUFFMAN_1_BYTE, CompressionMode.HUFFMAN_2_BYTE);

    private final PriorityQueueStrategy priorityQueueStrategy;
    private final CompressionMode preferredCompressionMode;
    private final java.util.Set<CompressionMode> allowedBlockHuffmanModes;
    private long treeBuildNanos;

    private HuffmanCode buildTreeAndTrack(FrequencyTable table) {
        long t0 = System.nanoTime();
        HuffmanCode root = HuffmanCode.buildFromFrequencies(table, createQueue());
        treeBuildNanos += System.nanoTime() - t0;
        return root;
    }

    public HuffmanCodec() {
        this(PriorityQueueStrategy.BINARY_HEAP, null, null);
    }

    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy) {
        this(priorityQueueStrategy, null, null);
    }

    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy, CompressionMode preferredCompressionMode) {
        this(priorityQueueStrategy, preferredCompressionMode, null);
    }

    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy,
                        CompressionMode preferredCompressionMode,
                        java.util.Set<CompressionMode> allowedBlockHuffmanModes) {
        this.priorityQueueStrategy = priorityQueueStrategy;
        this.preferredCompressionMode = preferredCompressionMode;
        this.allowedBlockHuffmanModes = allowedBlockHuffmanModes == null
            ? DEFAULT_BLOCK_SUB_MODES
            : java.util.Set.copyOf(allowedBlockHuffmanModes);
    }

    private boolean blockAllowsByteSubMode() {
        return allowedBlockHuffmanModes.contains(CompressionMode.HUFFMAN_1_BYTE);
    }

    private boolean blockAllowsWordSubMode() {
        return allowedBlockHuffmanModes.contains(CompressionMode.HUFFMAN_2_BYTE);
    }

    public CompressionResult compress(Path inputPath, Path outputPath) throws IOException {
        return compressWithReport(inputPath, outputPath).result();
    }

    public CompressionResult compress(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        return compressWithReport(inputPath, outputPath, listener).result();
    }

    public CompressionReport compressWithReport(Path inputPath, Path outputPath) throws IOException {
        return compressWithReport(inputPath, outputPath, null);
    }

    public CompressionReport compressWithReport(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        validatePaths(inputPath, outputPath);

        long startNanos = System.nanoTime();
        long originalSize = Files.size(inputPath);
        treeBuildNanos = 0L;

        BytePlan bytePlan = analyzeBytePlan(inputPath, originalSize, listener);
        WordPlan wordPlan = analyzeWordPlan(inputPath, originalSize, listener);
        BlockPlan blockPlan = analyzeBlockPlan(inputPath, originalSize, listener);
        long storedArchiveSize = ArchiveHeader.stored(originalSize).sizeInBytes() + originalSize;

        CompressionMode selectedMode = selectMode(storedArchiveSize, bytePlan, wordPlan, blockPlan);
        switch (selectedMode) {
            case STORED -> writeStoredArchive(inputPath, outputPath, originalSize, listener);
            case HUFFMAN_1_BYTE -> writeByteArchive(inputPath, outputPath, bytePlan, listener);
            case HUFFMAN_2_BYTE -> writeWordArchive(inputPath, outputPath, wordPlan, listener);
            case HUFFMAN_BLOCK -> writeBlockArchive(inputPath, outputPath, blockPlan, listener);
        }

        long archiveSize = Files.size(outputPath);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        long treeBuildMillis = treeBuildNanos / 1_000_000L;

        CompressionReport report = switch (selectedMode) {
            case STORED -> buildStoredReport(originalSize, archiveSize, elapsedMillis, treeBuildMillis, storedArchiveSize, bytePlan);
            case HUFFMAN_1_BYTE -> buildByteReport(originalSize, archiveSize, elapsedMillis, treeBuildMillis, bytePlan);
            case HUFFMAN_2_BYTE -> buildWordReport(originalSize, archiveSize, elapsedMillis, treeBuildMillis, wordPlan);
            case HUFFMAN_BLOCK -> buildBlockReport(originalSize, archiveSize, elapsedMillis, treeBuildMillis, blockPlan, bytePlan);
        };

        return report;
    }

    public DecompressionResult decompress(Path inputPath, Path outputPath) throws IOException {
        return decompressWithReport(inputPath, outputPath).result();
    }

    public DecompressionResult decompress(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        return decompressWithReport(inputPath, outputPath, listener).result();
    }

    public DecompressionReport decompressWithReport(Path inputPath, Path outputPath) throws IOException {
        return decompressWithReport(inputPath, outputPath, null);
    }

    public DecompressionReport decompressWithReport(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        validatePaths(inputPath, outputPath);

        long startNanos = System.nanoTime();
        long archiveSize = Files.size(inputPath);
        treeBuildNanos = 0L;

        try (InputStream rawInput = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE);
             DataInputStream input = new DataInputStream(rawInput);
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE)) {

            ArchiveHeader header = ArchiveHeader.read(input);
            ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.DECOMPRESSING, header.originalSize());

            List<HuffmanSymbolInfo> symbolInfos = List.of();
            HuffmanTreeNodeInfo treeInfo = null;

            switch (header.mode()) {
                case STORED -> copyExact(input, output, header.originalSize(), tracker);
                case HUFFMAN_1_BYTE -> {
                    FrequencyTable table = FrequencyTable.fromFrequencies(header.frequencies());
                    HuffmanCode root = buildTreeAndTrack(table);
                    HuffmanCode[] leaves = root == null ? new HuffmanCode[table.symbolSpaceSize()] : root.lookupBySymbol(table.symbolSpaceSize());
                    symbolInfos = buildSymbolInfos(table, leaves);
                    treeInfo = buildTreeInfo(root, table.totalCount(), "");
                    decompressBytePayload(input, output, table, root, header.originalSize(), tracker);
                }
                case HUFFMAN_2_BYTE -> {
                    FrequencyTable table = FrequencyTable.fromFrequencies(header.frequencies());
                    HuffmanCode root = buildTreeAndTrack(table);
                    HuffmanCode[] leaves = root == null ? new HuffmanCode[table.symbolSpaceSize()] : root.lookupBySymbol(table.symbolSpaceSize());
                    symbolInfos = buildSymbolInfos(table, leaves);
                    treeInfo = buildTreeInfo(root, table.totalCount(), "");
                    decompressWordPayload(input, output, table, root, header.originalSize(), tracker);
                }
                case HUFFMAN_BLOCK -> decompressBlockArchive(input, output, header.originalSize(), tracker);
            }

            output.flush();
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            long treeBuildMillis = treeBuildNanos / 1_000_000L;
            DecompressionResult result = new DecompressionResult(header.mode(), archiveSize, header.originalSize(), elapsedMillis, treeBuildMillis);
            return new DecompressionReport(result, symbolInfos, treeInfo);
        }
    }

    private BytePlan analyzeBytePlan(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        FrequencyTable table = analyzeByteFrequencies(inputPath, totalBytes, listener);
        HuffmanCode root = buildTreeAndTrack(table);
        HuffmanCode[] leaves = root == null ? new HuffmanCode[table.symbolSpaceSize()] : root.lookupBySymbol(table.symbolSpaceSize());
        long bitCount = totalBitCount(table, leaves);
        double entropy = table.entropy();
        double averageCodeLength = averageCodeLength(table, bitCount);
        ArchiveHeader header = ArchiveHeader.huffman1Byte(totalBytes, table.copyFrequencies());
        return new BytePlan(table, root, leaves, bitCount, entropy, averageCodeLength, header, header.sizeInBytes(), header.sizeInBytes() + bytesForBits(bitCount));
    }

    private WordPlan analyzeWordPlan(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        if (totalBytes < 2L) {
            return null;
        }

        FrequencyTable table = analyzeWordFrequencies(inputPath, totalBytes, listener);
        if (table.totalCount() == 0L) {
            return null;
        }

        HuffmanCode root = buildTreeAndTrack(table);
        HuffmanCode[] leaves = root == null ? new HuffmanCode[table.symbolSpaceSize()] : root.lookupBySymbol(table.symbolSpaceSize());
        long bitCount = totalBitCount(table, leaves);
        double entropy = table.entropy();
        double averageCodeLength = averageCodeLength(table, bitCount);
        ArchiveHeader header = ArchiveHeader.huffman2Byte(totalBytes, table.copyFrequencies());
        long estimatedArchiveSize = header.sizeInBytes() + bytesForBits(bitCount) + (totalBytes % 2L == 0L ? 0L : 1L);
        return new WordPlan(table, root, leaves, bitCount, entropy, averageCodeLength, header, header.sizeInBytes(), estimatedArchiveSize);
    }

    private BlockPlan analyzeBlockPlan(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        if (totalBytes == 0L) {
            return new BlockPlan(ANALYSIS_BLOCK_SIZE, List.of(), ArchiveHeader.block(0L), ArchiveHeader.block(0L).sizeInBytes(), ArchiveHeader.block(0L).sizeInBytes(), 0L, 0.0, 0.0);
        }

        int selectedBlockSize = selectBlockSize(inputPath, totalBytes);

        ArchiveHeader header = ArchiveHeader.block(totalBytes);
        List<BlockUnit> blocks = new ArrayList<>();
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.ANALYZING, totalBytes);

        long processedBytes = 0L;
        long metadataOverhead = header.sizeInBytes();
        long estimatedArchiveSize = header.sizeInBytes();
        long totalCompressedBits = 0L;
        long totalEncodedBytes = 0L;
        double weightedEntropy = 0.0;

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[selectedBlockSize];
            int read;
            while ((read = readBlock(input, buffer, selectedBlockSize)) > 0) {
                FrequencyTable byteTable = new FrequencyTable();
                byteTable.add(buffer, read);
                HuffmanCode[] byteLeaves = null;
                long byteBitCount = 0L;
                long byteMetadata = 0L;
                long byteSize = Long.MAX_VALUE;
                if (blockAllowsByteSubMode()) {
                    HuffmanCode byteRoot = buildTreeAndTrack(byteTable);
                    byteLeaves = byteRoot == null ? new HuffmanCode[byteTable.symbolSpaceSize()] : byteRoot.lookupBySymbol(byteTable.symbolSpaceSize());
                    byteBitCount = totalBitCount(byteTable, byteLeaves);
                    byteMetadata = BLOCK_HEADER_SIZE + Integer.BYTES + (long) byteTable.distinctSymbolCount() * BLOCK_BYTE_FREQUENCY_ENTRY_SIZE;
                    byteSize = byteMetadata + bytesForBits(byteBitCount);
                }

                FrequencyTable wordTable = null;
                HuffmanCode[] wordLeaves = null;
                long wordBitCount = 0L;
                long wordMetadata = 0L;
                long wordSize = Long.MAX_VALUE;
                if (blockAllowsWordSubMode() && read >= 2) {
                    wordTable = buildBlockWordFrequencyTable(buffer, read);
                    if (wordTable.totalCount() > 0L) {
                        HuffmanCode wordRoot = buildTreeAndTrack(wordTable);
                        wordLeaves = wordRoot == null ? new HuffmanCode[wordTable.symbolSpaceSize()] : wordRoot.lookupBySymbol(wordTable.symbolSpaceSize());
                        wordBitCount = totalBitCount(wordTable, wordLeaves);
                        wordMetadata = BLOCK_HEADER_SIZE + Integer.BYTES + (long) wordTable.distinctSymbolCount() * BLOCK_WORD_FREQUENCY_ENTRY_SIZE;
                        long trailing = (read & 1) == 1 ? 1L : 0L;
                        wordSize = wordMetadata + bytesForBits(wordBitCount) + trailing;
                    }
                }

                long storedSize = BLOCK_HEADER_SIZE + read;

                CompressionMode blockMode;
                long blockMetadata;
                long blockEncodedSize;
                long blockEncodedBits;
                if (wordSize < storedSize && wordSize <= byteSize) {
                    blockMode = CompressionMode.HUFFMAN_2_BYTE;
                    blockMetadata = wordMetadata;
                    blockEncodedSize = wordSize;
                    blockEncodedBits = wordBitCount;
                } else if (byteSize < storedSize) {
                    blockMode = CompressionMode.HUFFMAN_1_BYTE;
                    blockMetadata = byteMetadata;
                    blockEncodedSize = byteSize;
                    blockEncodedBits = byteBitCount;
                } else {
                    blockMode = CompressionMode.STORED;
                    blockMetadata = BLOCK_HEADER_SIZE;
                    blockEncodedSize = storedSize;
                    blockEncodedBits = 0L;
                }

                blocks.add(new BlockUnit(read, blockMode, byteTable, byteLeaves, wordTable, wordLeaves, blockEncodedBits, blockMetadata, blockEncodedSize));
                metadataOverhead += blockMetadata;
                estimatedArchiveSize += blockEncodedSize;
                if (blockMode != CompressionMode.STORED) {
                    totalCompressedBits += blockEncodedBits;
                    totalEncodedBytes += read;
                }
                weightedEntropy += byteTable.entropy() * read;

                processedBytes += read;
                tracker.update(processedBytes);
            }
        }

        tracker.complete(processedBytes);

        double averageCodeLength = totalEncodedBytes == 0L ? 0.0 : (double) totalCompressedBits / totalEncodedBytes;
        double entropy = totalBytes == 0L ? 0.0 : weightedEntropy / totalBytes;
        return new BlockPlan(selectedBlockSize, List.copyOf(blocks), header, metadataOverhead, estimatedArchiveSize, totalCompressedBits, entropy, averageCodeLength);
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

    private int selectBlockSize(Path inputPath, long totalBytes) throws IOException {
        int numCandidates = CANDIDATE_BLOCK_SIZES.length;
        long[][] runningByteFreqs = new long[numCandidates][256];
        long[][] runningWordFreqs = new long[numCandidates][WORD_SYMBOL_SPACE];
        int[][] runningWordVisited = new int[numCandidates][WORD_SYMBOL_SPACE];
        int[] runningWordVisitedCount = new int[numCandidates];
        int[] runningBytes = new int[numCandidates];
        long[] totalEstimates = new long[numCandidates];

        long headerSize = ArchiveHeader.block(totalBytes).sizeInBytes();
        Arrays.fill(totalEstimates, headerSize);

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[ANALYSIS_BLOCK_SIZE];
            long[] miniByteFreqs = new long[256];
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
                    for (int s = 0; s < 256; s++) {
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
                            runningBytes[c]
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
                    runningBytes[c]
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

    private long estimateBlockCompressedSize(long[] byteFreqs,
                                             long[] wordFreqs,
                                             int[] wordVisited,
                                             int wordVisitedCount,
                                             int blockBytes) {
        long storedSize = (long) BLOCK_HEADER_SIZE + blockBytes;
        long best = storedSize;

        if (blockAllowsByteSubMode()) {
            long byteSize = estimateByteHuffmanBlockSize(byteFreqs, blockBytes);
            if (byteSize < best) {
                best = byteSize;
            }
        }

        if (blockAllowsWordSubMode()) {
            long wordSize = estimateWordHuffmanBlockSize(wordFreqs, wordVisited, wordVisitedCount, blockBytes);
            if (wordSize < best) {
                best = wordSize;
            }
        }
        return best;
    }

    private long estimateByteHuffmanBlockSize(long[] frequencies, int blockBytes) {
        int distinctSymbols = 0;
        long totalSymbols = 0L;
        for (long f : frequencies) {
            if (f > 0L) {
                distinctSymbols++;
                totalSymbols += f;
            }
        }

        long metadata = (long) BLOCK_HEADER_SIZE + Integer.BYTES + (long) distinctSymbols * BLOCK_BYTE_FREQUENCY_ENTRY_SIZE;
        if (distinctSymbols <= 1) {
            return metadata;
        }

        double totalBits = shannonBits(frequencies, totalSymbols);
        return metadata + bytesForBits((long) Math.ceil(totalBits));
    }

    private long estimateWordHuffmanBlockSize(long[] wordFreqs, int[] visited, int visitedCount, int blockBytes) {
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
            long f = wordFreqs[visited[v]];
            totalBits += f * (logTotal - Math.log(f));
        }
        totalBits /= Math.log(2);

        return metadata + bytesForBits((long) Math.ceil(totalBits)) + trailing;
    }

    private double shannonBits(long[] frequencies, long totalSymbols) {
        double totalBits = 0.0;
        double logTotal = Math.log(totalSymbols);
        for (long f : frequencies) {
            if (f > 0L) {
                totalBits += f * (logTotal - Math.log(f));
            }
        }
        return totalBits / Math.log(2);
    }

    private static int readBlock(InputStream input, byte[] buffer, int maxBytes) throws IOException {
        int totalRead = 0;
        while (totalRead < maxBytes) {
            int read = input.read(buffer, totalRead, maxBytes - totalRead);
            if (read < 0) break;
            totalRead += read;
        }
        return totalRead;
    }

    private CompressionMode selectBestMode(long storedArchiveSize, BytePlan bytePlan, WordPlan wordPlan, BlockPlan blockPlan) {
        CompressionMode selected = CompressionMode.STORED;
        long bestSize = storedArchiveSize;

        if (bytePlan != null && bytePlan.estimatedArchiveSize < bestSize) {
            selected = CompressionMode.HUFFMAN_1_BYTE;
            bestSize = bytePlan.estimatedArchiveSize;
        }
        if (wordPlan != null && wordPlan.estimatedArchiveSize < bestSize) {
            selected = CompressionMode.HUFFMAN_2_BYTE;
            bestSize = wordPlan.estimatedArchiveSize;
        }
        if (blockPlan != null && blockPlan.estimatedArchiveSize < bestSize) {
            selected = CompressionMode.HUFFMAN_BLOCK;
        }
        return selected;
    }

    private CompressionMode selectMode(long storedArchiveSize, BytePlan bytePlan, WordPlan wordPlan, BlockPlan blockPlan) {
        if (preferredCompressionMode == null) {
            return selectBestMode(storedArchiveSize, bytePlan, wordPlan, blockPlan);
        }

        return switch (preferredCompressionMode) {
            case STORED -> CompressionMode.STORED;
            case HUFFMAN_1_BYTE -> bytePlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_1_BYTE;
            case HUFFMAN_2_BYTE -> wordPlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_2_BYTE;
            case HUFFMAN_BLOCK -> blockPlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_BLOCK;
        };
    }

    private CompressionReport buildStoredReport(long originalSize,
                                                long archiveSize,
                                                long elapsedMillis,
                                                long treeBuildMillis,
                                                long storedArchiveSize,
                                                BytePlan bytePlan) {
        CompressionResult result = new CompressionResult(
            CompressionMode.STORED,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            ArchiveHeader.stored(originalSize).sizeInBytes(),
            bytePlan == null ? 0 : bytePlan.table.distinctSymbolCount(),
            bytePlan == null ? 0L : bytePlan.bitCount,
            bytePlan == null ? 0.0 : bytePlan.entropy,
            bytePlan == null ? 0.0 : bytePlan.averageCodeLength,
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(
            result,
            bytePlan == null ? List.of() : buildSymbolInfos(bytePlan.table, bytePlan.leaves),
            bytePlan == null ? null : buildTreeInfo(bytePlan.root, bytePlan.table.totalCount(), "")
        );
    }

    private CompressionReport buildByteReport(long originalSize, long archiveSize, long elapsedMillis, long treeBuildMillis, BytePlan plan) {
        CompressionResult result = new CompressionResult(
            CompressionMode.HUFFMAN_1_BYTE,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            plan.metadataOverhead,
            plan.table.distinctSymbolCount(),
            plan.bitCount,
            plan.entropy,
            plan.averageCodeLength,
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(result, buildSymbolInfos(plan.table, plan.leaves), buildTreeInfo(plan.root, plan.table.totalCount(), ""));
    }

    private CompressionReport buildWordReport(long originalSize, long archiveSize, long elapsedMillis, long treeBuildMillis, WordPlan plan) {
        CompressionResult result = new CompressionResult(
            CompressionMode.HUFFMAN_2_BYTE,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            plan.metadataOverhead,
            plan.table.distinctSymbolCount(),
            plan.bitCount,
            plan.entropy,
            plan.averageCodeLength,
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(result, buildSymbolInfos(plan.table, plan.leaves), buildTreeInfo(plan.root, plan.table.totalCount(), ""));
    }

    private CompressionReport buildBlockReport(long originalSize,
                                               long archiveSize,
                                               long elapsedMillis,
                                               long treeBuildMillis,
                                               BlockPlan blockPlan,
                                               BytePlan bytePlan) {
        CompressionResult result = new CompressionResult(
            CompressionMode.HUFFMAN_BLOCK,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            blockPlan.metadataOverhead,
            bytePlan == null ? 0 : bytePlan.table.distinctSymbolCount(),
            blockPlan.totalCompressedBits,
            bytePlan == null ? 0.0 : bytePlan.entropy,
            blockPlan.averageCodeLength,
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(result, List.of(), null);
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

    private long totalBitCount(FrequencyTable table, HuffmanCode[] leaves) {
        long bitCount = 0L;
        for (int symbol = 0; symbol < table.symbolSpaceSize(); symbol++) {
            long frequency = table.frequencyOf(symbol);
            if (frequency == 0L) {
                continue;
            }
            bitCount += frequency * leaves[symbol].depth();
        }
        return bitCount;
    }

    private double averageCodeLength(FrequencyTable table, long totalBitCount) {
        long totalCount = table.totalCount();
        if (totalCount == 0L) {
            return 0.0;
        }
        return totalBitCount / (double) totalCount;
    }

    private List<HuffmanSymbolInfo> buildSymbolInfos(FrequencyTable table, HuffmanCode[] leaves) {
        if (table.distinctSymbolCount() == 0) {
            return List.of();
        }

        double totalCount = table.totalCount();
        List<HuffmanSymbolInfo> symbols = new ArrayList<>(table.distinctSymbolCount());
        for (int symbol = 0; symbol < table.symbolSpaceSize(); symbol++) {
            long frequency = table.frequencyOf(symbol);
            if (frequency == 0L) {
                continue;
            }
            symbols.add(new HuffmanSymbolInfo(symbol, frequency, frequency / totalCount, codeString(leaves[symbol].code())));
        }
        return List.copyOf(symbols);
    }

    private HuffmanTreeNodeInfo buildTreeInfo(HuffmanCode node, long totalCount, String code) {
        if (node == null) {
            return null;
        }
        return new HuffmanTreeNodeInfo(
            node.symbol(),
            node.frequency(),
            totalCount == 0L ? 0.0 : node.frequency() / (double) totalCount,
            code,
            node.isLeaf(),
            buildTreeInfo(node.min(), totalCount, code + '0'),
            buildTreeInfo(node.max(), totalCount, code + '1')
        );
    }

    private String codeString(byte[] code) {
        if (code.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(code.length);
        for (byte bit : code) {
            builder.append(bit == 0 ? '0' : '1');
        }
        return builder.toString();
    }

    private void writeStoredArchive(Path inputPath, Path outputPath, long originalSize, ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput)) {
            ArchiveHeader.stored(originalSize).write(output);
            writeStoredPayload(inputPath, output, originalSize, listener);
        }
    }

    private void writeByteArchive(Path inputPath, Path outputPath, BytePlan plan, ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput)) {
            plan.header.write(output);
            if (plan.table.distinctSymbolCount() <= 1) {
                new ProgressTracker(listener, ProgressPhase.COMPRESSING, plan.header.originalSize()).complete(plan.header.originalSize());
                return;
            }
            writeByteHuffmanPayload(inputPath, output, plan.leaves, plan.header.originalSize(), listener);
        }
    }

    private void writeWordArchive(Path inputPath, Path outputPath, WordPlan plan, ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput)) {
            plan.header.write(output);
            ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, plan.header.originalSize());
            try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
                BitOutputStream bitOutput = new BitOutputStream(output);
                int pendingByte = -1;
                int trailingByte = -1;
                byte[] buffer = new byte[BUFFER_SIZE];
                long processedBytes = 0L;
                int read;

                while ((read = input.read(buffer)) >= 0) {
                    for (int i = 0; i < read; i++) {
                        int value = buffer[i] & 0xFF;
                        if (pendingByte < 0) {
                            pendingByte = value;
                        } else {
                            bitOutput.write(plan.leaves[(pendingByte << 8) | value].code());
                            pendingByte = -1;
                        }
                    }
                    processedBytes += read;
                    tracker.update(processedBytes);
                }

                if (pendingByte >= 0) {
                    trailingByte = pendingByte;
                }

                bitOutput.finish();
                if (trailingByte >= 0) {
                    output.writeByte(trailingByte);
                }
                tracker.complete(processedBytes);
            }
        }
    }

    private void writeBlockArchive(Path inputPath, Path outputPath, BlockPlan blockPlan, ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput);
             InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {

            blockPlan.header.write(output);
            ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, blockPlan.header.originalSize());
            byte[] buffer = new byte[blockPlan.blockSize];
            long processedBytes = 0L;

            for (BlockUnit block : blockPlan.blocks) {
                int read = readBlock(input, buffer, block.blockSize);
                if (read != block.blockSize) {
                    throw new IOException("No s'ha pogut rellegir un bloc durant la compressio.");
                }

                output.writeInt(block.blockSize);
                output.writeByte(block.mode.id());

                switch (block.mode) {
                    case STORED -> output.write(buffer, 0, read);
                    case HUFFMAN_1_BYTE -> {
                        output.writeInt(block.byteTable.distinctSymbolCount());
                        writeByteFrequencyEntries(output, block.byteTable.copyFrequencies());
                        if (block.byteTable.distinctSymbolCount() > 1) {
                            BitOutputStream bitOutput = new BitOutputStream(output);
                            for (int i = 0; i < read; i++) {
                                bitOutput.write(block.byteLeaves[buffer[i] & 0xFF].code());
                            }
                            bitOutput.finish();
                        }
                    }
                    case HUFFMAN_2_BYTE -> {
                        output.writeInt(block.wordTable.distinctSymbolCount());
                        writeWordFrequencyEntries(output, block.wordTable.copyFrequencies());
                        int pairEnd = read - (read & 1);
                        if (block.wordTable.distinctSymbolCount() > 1) {
                            BitOutputStream bitOutput = new BitOutputStream(output);
                            for (int i = 0; i < pairEnd; i += 2) {
                                int word = ((buffer[i] & 0xFF) << 8) | (buffer[i + 1] & 0xFF);
                                bitOutput.write(block.wordLeaves[word].code());
                            }
                            bitOutput.finish();
                        }
                        if ((read & 1) == 1) {
                            output.writeByte(buffer[read - 1]);
                        }
                    }
                    default -> throw new IOException("Mode de bloc no suportat: " + block.mode);
                }

                processedBytes += read;
                tracker.update(processedBytes);
            }

            tracker.complete(processedBytes);
        }
    }

    private void writeByteFrequencyEntries(DataOutputStream output, long[] frequencies) throws IOException {
        for (int symbol = 0; symbol < frequencies.length; symbol++) {
            long frequency = frequencies[symbol];
            if (frequency == 0L) {
                continue;
            }
            output.writeByte(symbol);
            output.writeLong(frequency);
        }
    }

    private void writeWordFrequencyEntries(DataOutputStream output, long[] frequencies) throws IOException {
        for (int symbol = 0; symbol < frequencies.length; symbol++) {
            long frequency = frequencies[symbol];
            if (frequency == 0L) {
                continue;
            }
            output.writeShort(symbol);
            output.writeLong(frequency);
        }
    }

    private void writeByteHuffmanPayload(Path inputPath,
                                         OutputStream output,
                                         HuffmanCode[] leaves,
                                         long totalBytes,
                                         ProgressListener listener) throws IOException {
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, totalBytes);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            BitOutputStream bitOutput = new BitOutputStream(output);
            byte[] buffer = new byte[BUFFER_SIZE];
            long processedBytes = 0L;
            int read;

            while ((read = input.read(buffer)) >= 0) {
                for (int i = 0; i < read; i++) {
                    bitOutput.write(leaves[buffer[i] & 0xFF].code());
                }
                processedBytes += read;
                tracker.update(processedBytes);
            }

            bitOutput.finish();
            tracker.complete(processedBytes);
        }
    }

    private void decompressBytePayload(DataInputStream input,
                                       OutputStream output,
                                       FrequencyTable table,
                                       HuffmanCode root,
                                       long originalSize,
                                       ProgressTracker tracker) throws IOException {
        if (originalSize == 0L) {
            tracker.complete(0L);
            return;
        }
        if (table.distinctSymbolCount() == 1) {
            writeRepeatedByte(output, table.singleSymbol(), originalSize, tracker);
            return;
        }

        BitInputStream bitInput = new BitInputStream(input);
        HuffmanCode current = root;
        long restoredBytes = 0L;
        while (restoredBytes < originalSize) {
            int bit = bitInput.readBit();
            if (bit < 0) {
                throw new ArchiveFormatException("Final inesperat de la carrega Huffman.");
            }
            current = bit == 0 ? current.min() : current.max();
            if (current == null) {
                throw new ArchiveFormatException("Cami Huffman invalid dins la carrega.");
            }
            if (current.isLeaf()) {
                output.write(current.symbol());
                restoredBytes++;
                tracker.update(restoredBytes);
                current = root;
            }
        }
        tracker.complete(restoredBytes);
    }

    private void decompressWordPayload(DataInputStream input,
                                       OutputStream output,
                                       FrequencyTable table,
                                       HuffmanCode root,
                                       long originalSize,
                                       ProgressTracker tracker) throws IOException {
        long pairCount = originalSize / 2L;
        long restoredBytes = 0L;

        if (pairCount == 0L) {
            if (originalSize == 1L) {
                int trailing = input.read();
                if (trailing < 0) {
                    throw new ArchiveFormatException("Final inesperat del byte final de 2 bytes.");
                }
                output.write(trailing);
                tracker.complete(1L);
                return;
            }
            tracker.complete(0L);
            return;
        }

        if (table.distinctSymbolCount() == 1) {
            int symbol = table.singleSymbol();
            for (long i = 0; i < pairCount; i++) {
                output.write((symbol >>> 8) & 0xFF);
                output.write(symbol & 0xFF);
                restoredBytes += 2;
                tracker.update(restoredBytes);
            }
        } else {
            BitInputStream bitInput = new BitInputStream(input);
            HuffmanCode current = root;
            long decodedPairs = 0L;
            while (decodedPairs < pairCount) {
                int bit = bitInput.readBit();
                if (bit < 0) {
                    throw new ArchiveFormatException("Final inesperat de la carrega Huffman de 2 bytes.");
                }
                current = bit == 0 ? current.min() : current.max();
                if (current == null) {
                    throw new ArchiveFormatException("Cami Huffman invalid dins la carrega de 2 bytes.");
                }
                if (current.isLeaf()) {
                    int symbol = current.symbol();
                    output.write((symbol >>> 8) & 0xFF);
                    output.write(symbol & 0xFF);
                    restoredBytes += 2;
                    decodedPairs++;
                    tracker.update(restoredBytes);
                    current = root;
                }
            }
        }

        if ((originalSize & 1L) == 1L) {
            int trailing = input.read();
            if (trailing < 0) {
                throw new ArchiveFormatException("Final inesperat del byte final de 2 bytes.");
            }
            output.write(trailing);
            restoredBytes++;
            tracker.update(restoredBytes);
        }

        tracker.complete(restoredBytes);
    }

    private void decompressBlockArchive(DataInputStream input,
                                        OutputStream output,
                                        long originalSize,
                                        ProgressTracker tracker) throws IOException {
        long restoredBytes = 0L;
        while (restoredBytes < originalSize) {
            int blockSize = input.readInt();
            if (blockSize <= 0) {
                throw new ArchiveFormatException("La mida del bloc no pot ser zero o negativa.");
            }
            CompressionMode blockMode = CompressionMode.fromId(input.readUnsignedByte());

            switch (blockMode) {
                case STORED -> restoredBytes += copyExact(input, output, blockSize);
                case HUFFMAN_1_BYTE -> restoredBytes += decompressByteBlock(input, output, blockSize);
                case HUFFMAN_2_BYTE -> restoredBytes += decompressWordBlock(input, output, blockSize);
                default -> throw new ArchiveFormatException("Mode de bloc no valid: " + blockMode);
            }
            tracker.update(restoredBytes);
        }
        tracker.complete(restoredBytes);
    }

    private long decompressByteBlock(DataInputStream input, OutputStream output, int blockSize) throws IOException {
        int symbolCount = input.readInt();
        if (symbolCount < 0) {
            throw new ArchiveFormatException("El nombre de simbols del bloc no pot ser negatiu.");
        }

        long[] frequencies = new long[256];
        long total = 0L;
        for (int i = 0; i < symbolCount; i++) {
            int symbol = input.readUnsignedByte();
            long frequency = input.readLong();
            if (frequency <= 0L || frequencies[symbol] != 0L) {
                throw new ArchiveFormatException("Metadades de bloc Huffman no valides.");
            }
            frequencies[symbol] = frequency;
            total += frequency;
        }
        if (total != blockSize) {
            throw new ArchiveFormatException("La taula de frequencia del bloc no coincideix amb la mida del bloc.");
        }

        FrequencyTable table = FrequencyTable.fromFrequencies(frequencies);
        HuffmanCode root = buildTreeAndTrack(table);
        if (table.distinctSymbolCount() == 1) {
            return writeRepeatedByte(output, table.singleSymbol(), blockSize);
        }
        return decodeByteBlock(input, output, root, blockSize);
    }

    private long decompressWordBlock(DataInputStream input, OutputStream output, int blockSize) throws IOException {
        int symbolCount = input.readInt();
        if (symbolCount < 0) {
            throw new ArchiveFormatException("El nombre de simbols del bloc no pot ser negatiu.");
        }

        long[] frequencies = new long[WORD_SYMBOL_SPACE];
        long total = 0L;
        for (int i = 0; i < symbolCount; i++) {
            int symbol = input.readUnsignedShort();
            long frequency = input.readLong();
            if (frequency <= 0L || frequencies[symbol] != 0L) {
                throw new ArchiveFormatException("Metadades de bloc Huffman de 2 bytes no valides.");
            }
            frequencies[symbol] = frequency;
            total += frequency;
        }
        long pairCount = (long) blockSize / 2L;
        if (total != pairCount) {
            throw new ArchiveFormatException("La taula de bigrames del bloc no coincideix amb la mida del bloc.");
        }

        FrequencyTable table = FrequencyTable.fromFrequencies(frequencies);
        long restoredBytes = 0L;
        if (pairCount > 0L) {
            HuffmanCode root = buildTreeAndTrack(table);
            if (table.distinctSymbolCount() == 1) {
                int symbol = table.singleSymbol();
                for (long i = 0; i < pairCount; i++) {
                    output.write((symbol >>> 8) & 0xFF);
                    output.write(symbol & 0xFF);
                }
                restoredBytes += pairCount * 2L;
            } else {
                restoredBytes += decodeWordBlock(input, output, root, pairCount);
            }
        }

        if ((blockSize & 1) == 1) {
            int trailing = input.read();
            if (trailing < 0) {
                throw new ArchiveFormatException("Final inesperat del byte final del bloc de 2 bytes.");
            }
            output.write(trailing);
            restoredBytes++;
        }
        return restoredBytes;
    }

    private long decodeWordBlock(DataInputStream input, OutputStream output, HuffmanCode root, long pairCount) throws IOException {
        BitInputStream bitInput = new BitInputStream(input);
        HuffmanCode current = root;
        long decodedPairs = 0L;
        long restoredBytes = 0L;
        while (decodedPairs < pairCount) {
            int bit = bitInput.readBit();
            if (bit < 0) {
                throw new ArchiveFormatException("Final inesperat de la carrega Huffman del bloc de 2 bytes.");
            }
            current = bit == 0 ? current.min() : current.max();
            if (current == null) {
                throw new ArchiveFormatException("Cami Huffman invalid dins la carrega del bloc de 2 bytes.");
            }
            if (current.isLeaf()) {
                int symbol = current.symbol();
                output.write((symbol >>> 8) & 0xFF);
                output.write(symbol & 0xFF);
                restoredBytes += 2L;
                decodedPairs++;
                current = root;
            }
        }
        return restoredBytes;
    }

    private long decodeByteBlock(DataInputStream input, OutputStream output, HuffmanCode root, int blockSize) throws IOException {
        BitInputStream bitInput = new BitInputStream(input);
        HuffmanCode current = root;
        long restoredBytes = 0L;
        while (restoredBytes < blockSize) {
            int bit = bitInput.readBit();
            if (bit < 0) {
                throw new ArchiveFormatException("Final inesperat de la carrega Huffman del bloc.");
            }
            current = bit == 0 ? current.min() : current.max();
            if (current == null) {
                throw new ArchiveFormatException("Cami Huffman invalid dins la carrega del bloc.");
            }
            if (current.isLeaf()) {
                output.write(current.symbol());
                restoredBytes++;
                current = root;
            }
        }
        return restoredBytes;
    }

    private void writeStoredPayload(Path inputPath,
                                    OutputStream output,
                                    long totalBytes,
                                    ProgressListener listener) throws IOException {
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, totalBytes);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long processedBytes = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                processedBytes += read;
                tracker.update(processedBytes);
            }
            tracker.complete(processedBytes);
        }
    }

    private long writeRepeatedByte(OutputStream output, int symbol, long count) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        Arrays.fill(buffer, (byte) symbol);
        long written = 0L;
        while (written < count) {
            int chunkSize = (int) Math.min(buffer.length, count - written);
            output.write(buffer, 0, chunkSize);
            written += chunkSize;
        }
        return written;
    }

    private void writeRepeatedByte(OutputStream output, int symbol, long count, ProgressTracker tracker) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        Arrays.fill(buffer, (byte) symbol);
        long written = 0L;
        while (written < count) {
            int chunkSize = (int) Math.min(buffer.length, count - written);
            output.write(buffer, 0, chunkSize);
            written += chunkSize;
            tracker.update(written);
        }
        tracker.complete(written);
    }

    private long copyExact(InputStream input, OutputStream output, long expectedBytes) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copiedBytes = 0L;
        while (copiedBytes < expectedBytes) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, expectedBytes - copiedBytes));
            if (read < 0) {
                throw new ArchiveFormatException("Final inesperat de la carrega emmagatzemada.");
            }
            output.write(buffer, 0, read);
            copiedBytes += read;
        }
        return copiedBytes;
    }

    private void copyExact(InputStream input, OutputStream output, long expectedBytes, ProgressTracker tracker) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copiedBytes = 0L;
        while (copiedBytes < expectedBytes) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, expectedBytes - copiedBytes));
            if (read < 0) {
                throw new ArchiveFormatException("Final inesperat de la carrega emmagatzemada.");
            }
            output.write(buffer, 0, read);
            copiedBytes += read;
            tracker.update(copiedBytes);
        }
        tracker.complete(copiedBytes);
    }

    private static long bytesForBits(long bitCount) {
        return (bitCount + 7L) / 8L;
    }

    private NodeQueue<HuffmanCode> createQueue() {
        if (priorityQueueStrategy == PriorityQueueStrategy.BINARY_HEAP) return new BinaryHeapNodeQueue<>();
        if (priorityQueueStrategy == PriorityQueueStrategy.DICHOTOMIC_LIST) return new DichotomicListNodeQueue<>();
        if (priorityQueueStrategy == PriorityQueueStrategy.FIBONACCI_HEAP) return new FibonacciHeapNodeQueue<>();
        throw new IllegalArgumentException("Estrategia de cua no suportada: " + priorityQueueStrategy);
    }

    private static void validatePaths(Path inputPath, Path outputPath) {
        Path normalizedInput = inputPath.toAbsolutePath().normalize();
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (normalizedInput.equals(normalizedOutput)) {
            throw new IllegalArgumentException("Les rutes d'entrada i de sortida han de ser diferents.");
        }
    }

    private record BytePlan(FrequencyTable table,
                            HuffmanCode root,
                            HuffmanCode[] leaves,
                            long bitCount,
                            double entropy,
                            double averageCodeLength,
                            ArchiveHeader header,
                            long metadataOverhead,
                            long estimatedArchiveSize) {
    }

    private record WordPlan(FrequencyTable table,
                            HuffmanCode root,
                            HuffmanCode[] leaves,
                            long bitCount,
                            double entropy,
                            double averageCodeLength,
                            ArchiveHeader header,
                            long metadataOverhead,
                            long estimatedArchiveSize) {
    }

    private record BlockUnit(int blockSize,
                             CompressionMode mode,
                             FrequencyTable byteTable,
                             HuffmanCode[] byteLeaves,
                             FrequencyTable wordTable,
                             HuffmanCode[] wordLeaves,
                             long bitCount,
                             long metadataOverhead,
                             long encodedSize) {
    }

    private record BlockPlan(int blockSize,
                             List<BlockUnit> blocks,
                             ArchiveHeader header,
                             long metadataOverhead,
                             long estimatedArchiveSize,
                             long totalCompressedBits,
                             double entropy,
                             double averageCodeLength) {
    }
}
