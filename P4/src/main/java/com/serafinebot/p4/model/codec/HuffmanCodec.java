package com.serafinebot.p4.model.codec;

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
import com.serafinebot.p4.model.report.BlockReport;
import com.serafinebot.p4.model.report.CompressionReport;
import com.serafinebot.p4.model.report.CompressionResult;
import com.serafinebot.p4.model.report.DecompressionReport;
import com.serafinebot.p4.model.report.DecompressionResult;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * High-level model service for compressing and decompressing files with several Huffman strategies.
 *
 * <p>This class deliberately stays at the orchestration level: it analyzes candidate representations,
 * selects the smallest valid archive mode, and delegates byte-level archive I/O to
 * {@link HuffmanArchiveWriter} and {@link HuffmanArchiveReader}. Keeping those responsibilities
 * separate makes the report structure match the code structure.</p>
 */
public class HuffmanCodec {

    private static final int BUFFER_SIZE = 8192;
    private static final int WORD_SYMBOL_SPACE = 65536;
    private static final int BLOCK_HEADER_SIZE = Integer.BYTES + Byte.BYTES;
    private static final int BLOCK_BYTE_FREQUENCY_ENTRY_SIZE = Byte.BYTES + Long.BYTES;
    private static final int BLOCK_WORD_FREQUENCY_ENTRY_SIZE = Short.BYTES + Long.BYTES;
    private static final int ANALYSIS_BLOCK_SIZE = 4096;

    private final PriorityQueueStrategy priorityQueueStrategy;
    private final CompressionMode requestedCompressionMode;
    private final Set<CompressionMode> allowedBlockHuffmanModes;
    private long treeBuildNanos;

    private HuffmanCode buildTreeAndTrack(FrequencyTable table) {
        // Tree-build timing is measured independently because the assignment compares priority
        // queue strategies. Only this section should count toward that metric.
        long t0 = System.nanoTime();
        HuffmanCode root = HuffmanCode.buildFromFrequencies(table, createQueue());
        treeBuildNanos += System.nanoTime() - t0;
        return root;
    }

    private HuffmanCode[] lookupLeaves(FrequencyTable table, HuffmanCode root) {
        return root == null
            ? new HuffmanCode[table.symbolSpaceSize()]
            : root.lookupBySymbol(table.symbolSpaceSize());
    }

    public HuffmanCodec() {
        this(PriorityQueueStrategy.BINARY_HEAP, CompressionMode.AUTO);
    }

    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy) {
        this(priorityQueueStrategy, CompressionMode.AUTO);
    }

    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy, CompressionMode requestedCompressionMode) {
        this.priorityQueueStrategy = priorityQueueStrategy;
        this.requestedCompressionMode = requestedCompressionMode == null ? CompressionMode.AUTO : requestedCompressionMode;
        this.allowedBlockHuffmanModes = this.requestedCompressionMode.allowedBlockHuffmanModes();
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

        // The compressor estimates every supported representation before writing. This keeps the
        // "never bigger than the original except tiny files" rule explicit: STORED is always the
        // fallback candidate and Huffman modes must beat it to be selected automatically.
        BytePlan bytePlan = analyzeBytePlan(inputPath, originalSize, listener);
        WordPlan wordPlan = analyzeWordPlan(inputPath, originalSize, listener);
        BlockPlan blockPlan = analyzeBlockPlan(inputPath, originalSize, listener);
        long storedArchiveSize = ArchiveHeader.stored(originalSize).sizeInBytes() + originalSize;

        CompressionMode selectedMode = selectMode(storedArchiveSize, bytePlan, wordPlan, blockPlan);
        switch (selectedMode) {
            case STORED -> HuffmanArchiveWriter.writeStored(inputPath, outputPath, originalSize, listener);
            case HUFFMAN_1_BYTE -> HuffmanArchiveWriter.writeByte(inputPath, outputPath, bytePlan, listener);
            case HUFFMAN_2_BYTE -> HuffmanArchiveWriter.writeWord(inputPath, outputPath, wordPlan, listener);
            case HUFFMAN_BLOCK -> HuffmanArchiveWriter.writeBlock(inputPath, outputPath, blockPlan, listener);
            default -> throw new IllegalStateException("Mode d'arxiu seleccionat no valid: " + selectedMode);
        }

        long archiveSize = Files.size(outputPath);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        long treeBuildMillis = treeBuildNanos / 1_000_000L;

        CompressionReport report = switch (selectedMode) {
            case STORED -> buildStoredReport(originalSize, archiveSize, elapsedMillis, treeBuildMillis, bytePlan);
            case HUFFMAN_1_BYTE -> buildByteReport(originalSize, archiveSize, elapsedMillis, treeBuildMillis, bytePlan);
            case HUFFMAN_2_BYTE -> buildWordReport(originalSize, archiveSize, elapsedMillis, treeBuildMillis, wordPlan);
            case HUFFMAN_BLOCK -> buildBlockReport(originalSize, archiveSize, elapsedMillis, treeBuildMillis, blockPlan, bytePlan);
            default -> throw new IllegalStateException("Mode d'arxiu seleccionat no valid: " + selectedMode);
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

        DecodedArchive archive = new HuffmanArchiveReader(this::buildTreeAndTrack).read(inputPath, outputPath, listener);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        long treeBuildMillis = treeBuildNanos / 1_000_000L;
        DecompressionResult result = new DecompressionResult(
            archive.mode(),
            archiveSize,
            archive.originalSize(),
            elapsedMillis,
            treeBuildMillis
        );
        return new DecompressionReport(result, archive.symbols(), archive.tree(), archive.blocks());
    }

    private BytePlan analyzeBytePlan(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        FrequencyTable table = analyzeByteFrequencies(inputPath, totalBytes, listener);
        HuffmanCode root = buildTreeAndTrack(table);
        HuffmanCode[] leaves = lookupLeaves(table, root);
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
        HuffmanCode[] leaves = lookupLeaves(table, root);
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

        int selectedBlockSize = BlockSizeSelector.select(inputPath, totalBytes, blockAllowsByteSubMode(), blockAllowsWordSubMode());

        ArchiveHeader header = ArchiveHeader.block(totalBytes);
        List<BlockUnit> blocks = new ArrayList<>();
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.ANALYZING, totalBytes);

        long processedBytes = 0L;
        long metadataOverhead = header.sizeInBytes();
        long estimatedArchiveSize = header.sizeInBytes();
        long totalCompressedBits = 0L;
        long totalEncodedSymbols = 0L;
        double weightedEntropy = 0.0;

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[selectedBlockSize];
            int read;
            while ((read = readBlock(input, buffer, selectedBlockSize)) > 0) {
                // Each block chooses independently between byte Huffman, word Huffman, and stored.
                // That local decision is what lets the compressor handle files whose distribution
                // changes across the file instead of forcing one global tree to fit every region.
                FrequencyTable byteTable = new FrequencyTable();
                byteTable.add(buffer, read);
                HuffmanCode byteRoot = null;
                HuffmanCode[] byteLeaves = null;
                long byteBitCount = 0L;
                long byteMetadata = 0L;
                long byteSize = Long.MAX_VALUE;
                if (blockAllowsByteSubMode()) {
                    byteRoot = buildTreeAndTrack(byteTable);
                    byteLeaves = lookupLeaves(byteTable, byteRoot);
                    byteBitCount = totalBitCount(byteTable, byteLeaves);
                    byteMetadata = BLOCK_HEADER_SIZE + Integer.BYTES + (long) byteTable.distinctSymbolCount() * BLOCK_BYTE_FREQUENCY_ENTRY_SIZE;
                    byteSize = byteMetadata + bytesForBits(byteBitCount);
                }

                FrequencyTable wordTable = null;
                HuffmanCode wordRoot = null;
                HuffmanCode[] wordLeaves = null;
                long wordBitCount = 0L;
                long wordMetadata = 0L;
                long wordSize = Long.MAX_VALUE;
                if (blockAllowsWordSubMode() && read >= 2) {
                    wordTable = buildBlockWordFrequencyTable(buffer, read);
                    if (wordTable.totalCount() > 0L) {
                        wordRoot = buildTreeAndTrack(wordTable);
                        wordLeaves = lookupLeaves(wordTable, wordRoot);
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

                blocks.add(new BlockUnit(read, blockMode, byteTable, byteRoot, byteLeaves, wordTable, wordRoot, wordLeaves));
                metadataOverhead += blockMetadata;
                estimatedArchiveSize += blockEncodedSize;
                if (blockMode != CompressionMode.STORED) {
                    totalCompressedBits += blockEncodedBits;
                    totalEncodedSymbols += blockEncodedSymbols;
                }
                weightedEntropy += byteTable.entropy() * read;

                processedBytes += read;
                tracker.update(processedBytes);
            }
        }

        tracker.complete(processedBytes);

        double averageCodeLength = totalEncodedSymbols == 0L ? 0.0 : (double) totalCompressedBits / totalEncodedSymbols;
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

        if (bytePlan != null && bytePlan.estimatedArchiveSize() < bestSize) {
            selected = CompressionMode.HUFFMAN_1_BYTE;
            bestSize = bytePlan.estimatedArchiveSize();
        }
        if (wordPlan != null && wordPlan.estimatedArchiveSize() < bestSize) {
            selected = CompressionMode.HUFFMAN_2_BYTE;
            bestSize = wordPlan.estimatedArchiveSize();
        }
        if (blockPlan != null && blockPlan.estimatedArchiveSize() < bestSize) {
            selected = CompressionMode.HUFFMAN_BLOCK;
        }
        return selected;
    }

    private CompressionMode selectMode(long storedArchiveSize, BytePlan bytePlan, WordPlan wordPlan, BlockPlan blockPlan) {
        if (requestedCompressionMode.usesAutomaticSelection()) {
            return selectBestMode(storedArchiveSize, bytePlan, wordPlan, blockPlan);
        }

        return switch (requestedCompressionMode.archiveMode()) {
            case STORED -> CompressionMode.STORED;
            case HUFFMAN_1_BYTE -> bytePlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_1_BYTE;
            case HUFFMAN_2_BYTE -> wordPlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_2_BYTE;
            case HUFFMAN_BLOCK -> blockPlan == null ? CompressionMode.STORED : CompressionMode.HUFFMAN_BLOCK;
            case AUTO, HUFFMAN_BLOCK_1_BYTE, HUFFMAN_BLOCK_2_BYTE -> throw new IllegalStateException("Mode d'arxiu no valid: " + requestedCompressionMode);
        };
    }

    private CompressionReport buildStoredReport(long originalSize,
                                                long archiveSize,
                                                long elapsedMillis,
                                                long treeBuildMillis,
                                                BytePlan bytePlan) {
        CompressionResult result = new CompressionResult(
            CompressionMode.STORED,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            ArchiveHeader.stored(originalSize).sizeInBytes(),
            bytePlan == null ? 0 : bytePlan.table().distinctSymbolCount(),
            bytePlan == null ? 0L : bytePlan.bitCount(),
            bytePlan == null ? 0.0 : bytePlan.entropy(),
            bytePlan == null ? 0.0 : bytePlan.averageCodeLength(),
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(
            result,
            bytePlan == null ? List.of() : HuffmanReportBuilder.symbols(bytePlan.table(), bytePlan.leaves()),
            bytePlan == null ? null : HuffmanReportBuilder.tree(bytePlan.root(), bytePlan.table().totalCount())
        );
    }

    private CompressionReport buildByteReport(long originalSize, long archiveSize, long elapsedMillis, long treeBuildMillis, BytePlan plan) {
        CompressionResult result = new CompressionResult(
            CompressionMode.HUFFMAN_1_BYTE,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            plan.metadataOverhead(),
            plan.table().distinctSymbolCount(),
            plan.bitCount(),
            plan.entropy(),
            plan.averageCodeLength(),
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(result, HuffmanReportBuilder.symbols(plan.table(), plan.leaves()), HuffmanReportBuilder.tree(plan.root(), plan.table().totalCount()));
    }

    private CompressionReport buildWordReport(long originalSize, long archiveSize, long elapsedMillis, long treeBuildMillis, WordPlan plan) {
        CompressionResult result = new CompressionResult(
            CompressionMode.HUFFMAN_2_BYTE,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            plan.metadataOverhead(),
            plan.table().distinctSymbolCount(),
            plan.bitCount(),
            plan.entropy(),
            plan.averageCodeLength(),
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(result, HuffmanReportBuilder.symbols(plan.table(), plan.leaves()), HuffmanReportBuilder.tree(plan.root(), plan.table().totalCount()));
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
            blockPlan.metadataOverhead(),
            bytePlan == null ? 0 : bytePlan.table().distinctSymbolCount(),
            blockPlan.totalCompressedBits(),
            bytePlan == null ? 0.0 : bytePlan.entropy(),
            blockPlan.averageCodeLength(),
            elapsedMillis,
            treeBuildMillis
        );
        List<BlockReport> blockReports = new ArrayList<>(blockPlan.blocks().size());
        for (BlockUnit block : blockPlan.blocks()) {
            blockReports.add(buildBlockReportEntry(block));
        }
        return new CompressionReport(result, List.of(), null, blockReports);
    }

    private BlockReport buildBlockReportEntry(BlockUnit block) {
        return switch (block.mode()) {
            case HUFFMAN_1_BYTE -> new BlockReport(
                block.blockSize(),
                block.mode(),
                HuffmanReportBuilder.tree(block.byteRoot(), block.byteTable().totalCount()),
                HuffmanReportBuilder.symbols(block.byteTable(), block.byteLeaves())
            );
            case HUFFMAN_2_BYTE -> new BlockReport(
                block.blockSize(),
                block.mode(),
                HuffmanReportBuilder.tree(block.wordRoot(), block.wordTable().totalCount()),
                HuffmanReportBuilder.symbols(block.wordTable(), block.wordLeaves())
            );
            default -> new BlockReport(block.blockSize(), block.mode(), null, List.of());
        };
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

    private static long bytesForBits(long bitCount) {
        return (bitCount + 7L) / 8L;
    }

    private NodeQueue<HuffmanCode> createQueue() {
        return switch (priorityQueueStrategy) {
            case BINARY_HEAP -> new BinaryHeapNodeQueue<>();
            case DICHOTOMIC_LIST -> new DichotomicListNodeQueue<>();
            case FIBONACCI_HEAP -> new FibonacciHeapNodeQueue<>();
        };
    }

    private static void validatePaths(Path inputPath, Path outputPath) {
        Path normalizedInput = inputPath.toAbsolutePath().normalize();
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (normalizedInput.equals(normalizedOutput)) {
            throw new IllegalArgumentException("Les rutes d'entrada i de sortida han de ser diferents.");
        }
    }

}
