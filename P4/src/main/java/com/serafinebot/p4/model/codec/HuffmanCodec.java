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
import com.serafinebot.p4.model.queue.OrderedListNodeQueue;
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
    private static final int BLOCK_SIZE = 4096;
    private static final int BLOCK_HEADER_SIZE = Short.BYTES + Byte.BYTES;
    private static final int BLOCK_BYTE_FREQUENCY_ENTRY_SIZE = 9;

    private final PriorityQueueStrategy priorityQueueStrategy;

    public HuffmanCodec() {
        this(PriorityQueueStrategy.BINARY_HEAP);
    }

    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy) {
        this.priorityQueueStrategy = priorityQueueStrategy;
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

        BytePlan bytePlan = analyzeBytePlan(inputPath, originalSize, listener);
        WordPlan wordPlan = analyzeWordPlan(inputPath, originalSize, listener);
        BlockPlan blockPlan = analyzeBlockPlan(inputPath, originalSize, listener);
        long storedArchiveSize = ArchiveHeader.stored(originalSize).sizeInBytes() + originalSize;

        CompressionMode selectedMode = selectBestMode(storedArchiveSize, bytePlan, wordPlan, blockPlan);
        switch (selectedMode) {
            case STORED -> writeStoredArchive(inputPath, outputPath, originalSize, listener);
            case HUFFMAN_1_BYTE -> writeByteArchive(inputPath, outputPath, bytePlan, listener);
            case HUFFMAN_2_BYTE -> writeWordArchive(inputPath, outputPath, wordPlan, listener);
            case HUFFMAN_BLOCK -> writeBlockArchive(inputPath, outputPath, blockPlan, listener);
        }

        long archiveSize = Files.size(outputPath);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        CompressionReport report = switch (selectedMode) {
            case STORED -> buildStoredReport(originalSize, archiveSize, elapsedMillis, storedArchiveSize, bytePlan);
            case HUFFMAN_1_BYTE -> buildByteReport(originalSize, archiveSize, elapsedMillis, bytePlan);
            case HUFFMAN_2_BYTE -> buildWordReport(originalSize, archiveSize, elapsedMillis, wordPlan);
            case HUFFMAN_BLOCK -> buildBlockReport(originalSize, archiveSize, elapsedMillis, blockPlan, bytePlan);
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
                    HuffmanCode root = HuffmanCode.buildFromFrequencies(table, createQueue());
                    HuffmanCode[] leaves = root == null ? new HuffmanCode[table.symbolSpaceSize()] : root.lookupBySymbol(table.symbolSpaceSize());
                    symbolInfos = buildSymbolInfos(table, leaves);
                    treeInfo = buildTreeInfo(root, table.totalCount(), "");
                    decompressBytePayload(input, output, table, root, header.originalSize(), tracker);
                }
                case HUFFMAN_2_BYTE -> {
                    FrequencyTable table = FrequencyTable.fromFrequencies(header.frequencies());
                    HuffmanCode root = HuffmanCode.buildFromFrequencies(table, createQueue());
                    HuffmanCode[] leaves = root == null ? new HuffmanCode[table.symbolSpaceSize()] : root.lookupBySymbol(table.symbolSpaceSize());
                    symbolInfos = buildSymbolInfos(table, leaves);
                    treeInfo = buildTreeInfo(root, table.totalCount(), "");
                    decompressWordPayload(input, output, table, root, header.originalSize(), tracker);
                }
                case HUFFMAN_BLOCK -> decompressBlockArchive(input, output, header.originalSize(), tracker);
            }

            output.flush();
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            DecompressionResult result = new DecompressionResult(header.mode(), archiveSize, header.originalSize(), elapsedMillis);
            return new DecompressionReport(result, symbolInfos, treeInfo);
        }
    }

    private BytePlan analyzeBytePlan(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
        FrequencyTable table = analyzeByteFrequencies(inputPath, totalBytes, listener);
        HuffmanCode root = HuffmanCode.buildFromFrequencies(table, createQueue());
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

        HuffmanCode root = HuffmanCode.buildFromFrequencies(table, createQueue());
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
            return new BlockPlan(List.of(), ArchiveHeader.block(0L), ArchiveHeader.block(0L).sizeInBytes(), ArchiveHeader.block(0L).sizeInBytes(), 0L, 0.0, 0.0);
        }

        ArchiveHeader header = ArchiveHeader.block(totalBytes);
        List<BlockUnit> blocks = new ArrayList<>();
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.ANALYZING, totalBytes);

        long processedBytes = 0L;
        long metadataOverhead = header.sizeInBytes();
        long estimatedArchiveSize = header.sizeInBytes();
        long totalCompressedBits = 0L;
        double weightedAverageCodeLength = 0.0;
        double weightedEntropy = 0.0;

        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[BLOCK_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                FrequencyTable table = new FrequencyTable();
                table.add(buffer, read);

                HuffmanCode root = HuffmanCode.buildFromFrequencies(table, createQueue());
                    HuffmanCode[] leaves = root == null ? new HuffmanCode[table.symbolSpaceSize()] : root.lookupBySymbol(table.symbolSpaceSize());
                long bitCount = totalBitCount(table, leaves);
                long huffmanMetadata = BLOCK_HEADER_SIZE + Integer.BYTES + (long) table.distinctSymbolCount() * BLOCK_BYTE_FREQUENCY_ENTRY_SIZE;
                long huffmanSize = huffmanMetadata + bytesForBits(bitCount);
                long storedSize = BLOCK_HEADER_SIZE + read;

                CompressionMode blockMode = huffmanSize < storedSize ? CompressionMode.HUFFMAN_1_BYTE : CompressionMode.STORED;
                long blockMetadata = blockMode == CompressionMode.HUFFMAN_1_BYTE ? huffmanMetadata : BLOCK_HEADER_SIZE;
                long blockEncodedSize = blockMode == CompressionMode.HUFFMAN_1_BYTE ? huffmanSize : storedSize;
                double entropy = table.entropy();
                double averageCodeLength = averageCodeLength(table, bitCount);

                blocks.add(new BlockUnit(read, blockMode, table, leaves, bitCount, blockMetadata, blockEncodedSize));
                metadataOverhead += blockMetadata;
                estimatedArchiveSize += blockEncodedSize;
                if (blockMode == CompressionMode.HUFFMAN_1_BYTE) {
                    totalCompressedBits += bitCount;
                    weightedAverageCodeLength += averageCodeLength * read;
                }
                weightedEntropy += entropy * read;

                processedBytes += read;
                tracker.update(processedBytes);
            }
        }

        tracker.complete(processedBytes);

        double averageCodeLength = totalBytes == 0L ? 0.0 : weightedAverageCodeLength / totalBytes;
        double entropy = totalBytes == 0L ? 0.0 : weightedEntropy / totalBytes;
        return new BlockPlan(List.copyOf(blocks), header, metadataOverhead, estimatedArchiveSize, totalCompressedBits, entropy, averageCodeLength);
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

    private CompressionReport buildStoredReport(long originalSize,
                                                long archiveSize,
                                                long elapsedMillis,
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
            elapsedMillis
        );
        return new CompressionReport(
            result,
            bytePlan == null ? List.of() : buildSymbolInfos(bytePlan.table, bytePlan.leaves),
            bytePlan == null ? null : buildTreeInfo(bytePlan.root, bytePlan.table.totalCount(), "")
        );
    }

    private CompressionReport buildByteReport(long originalSize, long archiveSize, long elapsedMillis, BytePlan plan) {
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
            elapsedMillis
        );
        return new CompressionReport(result, buildSymbolInfos(plan.table, plan.leaves), buildTreeInfo(plan.root, plan.table.totalCount(), ""));
    }

    private CompressionReport buildWordReport(long originalSize, long archiveSize, long elapsedMillis, WordPlan plan) {
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
            elapsedMillis
        );
        return new CompressionReport(result, buildSymbolInfos(plan.table, plan.leaves), buildTreeInfo(plan.root, plan.table.totalCount(), ""));
    }

    private CompressionReport buildBlockReport(long originalSize,
                                               long archiveSize,
                                               long elapsedMillis,
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
            elapsedMillis
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
            byte[] buffer = new byte[BLOCK_SIZE];
            long processedBytes = 0L;

            for (BlockUnit block : blockPlan.blocks) {
                int read = input.read(buffer, 0, block.blockSize);
                if (read != block.blockSize) {
                    throw new IOException("No s'ha pogut rellegir un bloc durant la compressio.");
                }

                output.writeShort(block.blockSize);
                output.writeByte(block.mode.id());

                if (block.mode == CompressionMode.STORED) {
                    output.write(buffer, 0, read);
                } else {
                    output.writeInt(block.table.distinctSymbolCount());
                    writeByteFrequencyEntries(output, block.table.copyFrequencies());
                    if (block.table.distinctSymbolCount() > 1) {
                        BitOutputStream bitOutput = new BitOutputStream(output);
                        for (int i = 0; i < read; i++) {
                            bitOutput.write(block.leaves[buffer[i] & 0xFF].code());
                        }
                        bitOutput.finish();
                    }
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
            int blockSize = input.readUnsignedShort();
            CompressionMode blockMode = CompressionMode.fromId(input.readUnsignedByte());

            if (blockMode == CompressionMode.STORED) {
                restoredBytes += copyExact(input, output, blockSize);
                tracker.update(restoredBytes);
                continue;
            }
            if (blockMode != CompressionMode.HUFFMAN_1_BYTE) {
                throw new ArchiveFormatException("Mode de bloc no valid: " + blockMode);
            }

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
            HuffmanCode root = HuffmanCode.buildFromFrequencies(table, createQueue());
            if (table.distinctSymbolCount() == 1) {
                restoredBytes += writeRepeatedByte(output, table.singleSymbol(), blockSize);
            } else {
                restoredBytes += decodeByteBlock(input, output, root, blockSize);
            }
            tracker.update(restoredBytes);
        }
        tracker.complete(restoredBytes);
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
        if (priorityQueueStrategy == PriorityQueueStrategy.ORDERED_LIST) return new OrderedListNodeQueue<>();
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
                             FrequencyTable table,
                             HuffmanCode[] leaves,
                             long bitCount,
                             long metadataOverhead,
                             long encodedSize) {
    }

    private record BlockPlan(List<BlockUnit> blocks,
                             ArchiveHeader header,
                             long metadataOverhead,
                             long estimatedArchiveSize,
                             long totalCompressedBits,
                             double entropy,
                             double averageCodeLength) {
    }
}
