package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveFormatException;
import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.progress.ProgressListener;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressTracker;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.BlockReport;
import com.serafinebot.p4.model.report.HuffmanSymbolInfo;
import com.serafinebot.p4.model.report.HuffmanTreeNodeInfo;
import com.serafinebot.p4.util.BitInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads an archive and restores the original payload.
 */
final class HuffmanArchiveReader {

    private static final int BUFFER_SIZE = 8192;
    private static final int BYTE_SYMBOL_SPACE = 256;
    private static final int WORD_SYMBOL_SPACE = 65536;

    private final PriorityQueueStrategy priorityQueueStrategy;
    private long treeBuildNanos;

    HuffmanArchiveReader(PriorityQueueStrategy priorityQueueStrategy) {
        this.priorityQueueStrategy = priorityQueueStrategy;
    }

    DecodedArchive read(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        try (InputStream rawInput = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE);
             DataInputStream input = new DataInputStream(rawInput);
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE)) {

            ArchiveHeader header = ArchiveHeader.read(input);
            ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.DECOMPRESSING, header.originalSize());

            List<HuffmanSymbolInfo> symbolInfos = List.of();
            HuffmanTreeNodeInfo treeInfo = null;
            List<BlockReport> blockReports = List.of();

            switch (header.mode()) {
                case STORED -> copyExact(input, output, header.originalSize(), tracker);
                case HUFFMAN_1_BYTE -> {
                    FrequencyTable table = FrequencyTable.fromFrequencies(header.frequencies());
                    HuffmanCode root = buildTree(table);
                    HuffmanCode[] leaves = HuffmanCode.lookupBySymbol(root, table.symbolSpaceSize());
                    symbolInfos = HuffmanReport.symbols(table, leaves);
                    treeInfo = HuffmanReport.tree(root, table.totalCount());
                    decompressBytePayload(input, output, table, root, header.originalSize(), tracker);
                }
                case HUFFMAN_2_BYTE -> {
                    FrequencyTable table = FrequencyTable.fromFrequencies(header.frequencies());
                    HuffmanCode root = buildTree(table);
                    HuffmanCode[] leaves = HuffmanCode.lookupBySymbol(root, table.symbolSpaceSize());
                    symbolInfos = HuffmanReport.symbols(table, leaves);
                    treeInfo = HuffmanReport.tree(root, table.totalCount());
                    decompressWordPayload(input, output, table, root, header.originalSize(), tracker);
                }
                case HUFFMAN_BLOCK -> blockReports = decompressBlockArchive(input, output, header.originalSize(), tracker);
            }

            output.flush();
            return new DecodedArchive(header.mode(), header.originalSize(), symbolInfos, treeInfo, blockReports);
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

        long restoredBytes = decodeByteSymbols(
            new BitInputStream(input),
            output,
            root,
            originalSize,
            "Final inesperat de la carrega Huffman.",
            "Cami Huffman invalid dins la carrega.",
            tracker
        );
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
            restoredBytes += decodeWordSymbols(
                new BitInputStream(input),
                output,
                root,
                pairCount,
                "Final inesperat de la carrega Huffman de 2 bytes.",
                "Cami Huffman invalid dins la carrega de 2 bytes.",
                tracker
            );
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

    private List<BlockReport> decompressBlockArchive(DataInputStream input,
                                                     OutputStream output,
                                                     long originalSize,
                                                     ProgressTracker tracker) throws IOException {
        List<BlockReport> blockReports = new ArrayList<>();
        long restoredBytes = 0L;
        while (restoredBytes < originalSize) {
            int blockSize = input.readInt();
            if (blockSize <= 0) {
                throw new ArchiveFormatException("La mida del bloc no pot ser zero o negativa.");
            }
            CompressionMode blockMode = CompressionMode.fromId(input.readUnsignedByte());

            switch (blockMode) {
                case STORED -> {
                    restoredBytes += copyExact(input, output, blockSize);
                    blockReports.add(new BlockReport(blockSize, blockMode, null, List.of()));
                }
                case HUFFMAN_1_BYTE -> {
                    BlockDecodeResult result = decompressByteBlock(input, output, blockSize);
                    restoredBytes += result.bytesWritten();
                    blockReports.add(new BlockReport(blockSize, blockMode, result.tree(), result.symbols()));
                }
                case HUFFMAN_2_BYTE -> {
                    BlockDecodeResult result = decompressWordBlock(input, output, blockSize);
                    restoredBytes += result.bytesWritten();
                    blockReports.add(new BlockReport(blockSize, blockMode, result.tree(), result.symbols()));
                }
                default -> throw new ArchiveFormatException("Mode de bloc no valid: " + blockMode);
            }
            tracker.update(restoredBytes);
        }
        tracker.complete(restoredBytes);
        return List.copyOf(blockReports);
    }

    private BlockDecodeResult decompressByteBlock(DataInputStream input, OutputStream output, int blockSize) throws IOException {
        FrequencyTable table = readByteBlockFrequencyTable(input, blockSize);
        HuffmanCode root = buildTree(table);
        HuffmanCode[] leaves = HuffmanCode.lookupBySymbol(root, table.symbolSpaceSize());
        List<HuffmanSymbolInfo> symbols = HuffmanReport.symbols(table, leaves);
        HuffmanTreeNodeInfo tree = HuffmanReport.tree(root, table.totalCount());
        long written;
        if (table.distinctSymbolCount() == 1) {
            written = writeRepeatedByte(output, table.singleSymbol(), blockSize);
        } else {
            written = decodeByteBlock(input, output, root, blockSize);
        }
        return new BlockDecodeResult(written, tree, symbols);
    }

    private FrequencyTable readByteBlockFrequencyTable(DataInputStream input, int blockSize) throws IOException {
        int symbolCount = input.readInt();
        if (symbolCount < 0) {
            throw new ArchiveFormatException("El nombre de simbols del bloc no pot ser negatiu.");
        }

        long[] frequencies = new long[BYTE_SYMBOL_SPACE];
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

        return FrequencyTable.fromFrequencies(frequencies);
    }

    private BlockDecodeResult decompressWordBlock(DataInputStream input, OutputStream output, int blockSize) throws IOException {
        FrequencyTable table = readWordBlockFrequencyTable(input, blockSize);
        long pairCount = (long) blockSize / 2L;
        long restoredBytes = 0L;
        HuffmanTreeNodeInfo tree = null;
        List<HuffmanSymbolInfo> symbols = List.of();
        if (pairCount > 0L) {
            HuffmanCode root = buildTree(table);
            HuffmanCode[] leaves = HuffmanCode.lookupBySymbol(root, table.symbolSpaceSize());
            symbols = HuffmanReport.symbols(table, leaves);
            tree = HuffmanReport.tree(root, table.totalCount());
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
        return new BlockDecodeResult(restoredBytes, tree, symbols);
    }

    private FrequencyTable readWordBlockFrequencyTable(DataInputStream input, int blockSize) throws IOException {
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

        return FrequencyTable.fromFrequencies(frequencies);
    }

    private long decodeWordBlock(DataInputStream input, OutputStream output, HuffmanCode root, long pairCount) throws IOException {
        return decodeWordSymbols(
            new BitInputStream(input),
            output,
            root,
            pairCount,
            "Final inesperat de la carrega Huffman del bloc de 2 bytes.",
            "Cami Huffman invalid dins la carrega del bloc de 2 bytes.",
            null
        );
    }

    private long decodeByteBlock(DataInputStream input, OutputStream output, HuffmanCode root, int blockSize) throws IOException {
        return decodeByteSymbols(
            new BitInputStream(input),
            output,
            root,
            blockSize,
            "Final inesperat de la carrega Huffman del bloc.",
            "Cami Huffman invalid dins la carrega del bloc.",
            null
        );
    }

    private long decodeWordSymbols(BitInputStream bitInput,
                                   OutputStream output,
                                   HuffmanCode root,
                                   long pairCount,
                                   String eofMessage,
                                   String invalidPathMessage,
                                   ProgressTracker tracker) throws IOException {
        HuffmanCode current = root;
        long decodedPairs = 0L;
        long restoredBytes = 0L;
        while (decodedPairs < pairCount) {
            int bit = bitInput.readBit();
            if (bit < 0) {
                throw new ArchiveFormatException(eofMessage);
            }
            current = bit == 0 ? current.min() : current.max();
            if (current == null) {
                throw new ArchiveFormatException(invalidPathMessage);
            }
            if (current.isLeaf()) {
                int symbol = current.symbol();
                output.write((symbol >>> 8) & 0xFF);
                output.write(symbol & 0xFF);
                restoredBytes += 2L;
                decodedPairs++;
                if (tracker != null) {
                    tracker.update(restoredBytes);
                }
                current = root;
            }
        }
        return restoredBytes;
    }

    private long decodeByteSymbols(BitInputStream bitInput,
                                   OutputStream output,
                                   HuffmanCode root,
                                   long symbolCount,
                                   String eofMessage,
                                   String invalidPathMessage,
                                   ProgressTracker tracker) throws IOException {
        HuffmanCode current = root;
        long restoredBytes = 0L;
        while (restoredBytes < symbolCount) {
            int bit = bitInput.readBit();
            if (bit < 0) {
                throw new ArchiveFormatException(eofMessage);
            }
            current = bit == 0 ? current.min() : current.max();
            if (current == null) {
                throw new ArchiveFormatException(invalidPathMessage);
            }
            if (current.isLeaf()) {
                output.write(current.symbol());
                restoredBytes++;
                if (tracker != null) {
                    tracker.update(restoredBytes);
                }
                current = root;
            }
        }
        return restoredBytes;
    }

    private long writeRepeatedByte(OutputStream output, int symbol, long count) throws IOException {
        return writeRepeatedByteInternal(output, symbol, count, null);
    }

    private void writeRepeatedByte(OutputStream output, int symbol, long count, ProgressTracker tracker) throws IOException {
        long written = writeRepeatedByteInternal(output, symbol, count, tracker);
        tracker.complete(written);
    }

    private long writeRepeatedByteInternal(OutputStream output,
                                           int symbol,
                                           long count,
                                           ProgressTracker tracker) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        Arrays.fill(buffer, (byte) symbol);
        long written = 0L;
        while (written < count) {
            int chunkSize = (int) Math.min(buffer.length, count - written);
            output.write(buffer, 0, chunkSize);
            written += chunkSize;
            if (tracker != null) {
                tracker.update(written);
            }
        }
        return written;
    }

    private long copyExact(InputStream input, OutputStream output, long expectedBytes) throws IOException {
        return copyExactInternal(input, output, expectedBytes, null);
    }

    private void copyExact(InputStream input, OutputStream output, long expectedBytes, ProgressTracker tracker) throws IOException {
        long copiedBytes = copyExactInternal(input, output, expectedBytes, tracker);
        tracker.complete(copiedBytes);
    }

    private long copyExactInternal(InputStream input,
                                   OutputStream output,
                                   long expectedBytes,
                                   ProgressTracker tracker) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copiedBytes = 0L;
        while (copiedBytes < expectedBytes) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, expectedBytes - copiedBytes));
            if (read < 0) {
                throw new ArchiveFormatException("Final inesperat de la carrega emmagatzemada.");
            }
            output.write(buffer, 0, read);
            copiedBytes += read;
            if (tracker != null) {
                tracker.update(copiedBytes);
            }
        }
        return copiedBytes;
    }

    long treeBuildMillis() {
        return treeBuildNanos / 1_000_000L;
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
