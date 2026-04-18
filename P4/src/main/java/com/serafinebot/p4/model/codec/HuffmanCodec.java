package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveFormatException;
import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.progress.ProgressListener;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressTracker;
import com.serafinebot.p4.model.queue.BinaryHeapNodeQueue;
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
 * High-level model service for compressing and decompressing files with Huffman coding.
 *
 * <p>The codec works on raw bytes, not text, so it can process arbitrary file types. It performs
 * a first pass to count symbol frequencies, builds a deterministic Huffman tree, decides whether
 * the Huffman archive is smaller than the stored alternative, and finally writes the archive in the
 * project's custom binary format.</p>
 */
public class HuffmanCodec {

    private static final int BUFFER_SIZE = 8192;

    private final PriorityQueueStrategy priorityQueueStrategy;

    /**
     * Creates a codec that uses the default priority queue strategy.
     */
    public HuffmanCodec() {
        this(PriorityQueueStrategy.BINARY_HEAP);
    }

    /**
     * Creates a codec with an explicit priority queue strategy for Huffman tree construction.
     *
     * @param priorityQueueStrategy queue implementation strategy used when building the tree
     */
    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy) {
        this.priorityQueueStrategy = priorityQueueStrategy;
    }

    /**
     * Compresses a file without progress callbacks.
     *
     * @param inputPath input file to compress
     * @param outputPath destination archive path, typically ending in {@code .hff}
     * @return compression statistics for the completed operation
     * @throws IOException if the input cannot be read, the output cannot be written, or the
     *     archive cannot be created
     */
    public CompressionResult compress(Path inputPath, Path outputPath) throws IOException {
        return compressWithReport(inputPath, outputPath).result();
    }

    /**
     * Compresses a file and optionally reports progress snapshots to the caller.
     *
     * @param inputPath input file to compress
     * @param outputPath destination archive path, typically ending in {@code .hff}
     * @param listener optional progress callback, or {@code null} for no progress reporting
     * @return compression statistics for the completed operation
     * @throws IOException if the input cannot be read, the output cannot be written, or the
     *     archive cannot be created
     */
    public CompressionResult compress(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        return compressWithReport(inputPath, outputPath, listener).result();
    }

    /**
     * Compresses a file and returns both raw statistics and GUI-friendly Huffman metadata.
     */
    public CompressionReport compressWithReport(Path inputPath, Path outputPath) throws IOException {
        return compressWithReport(inputPath, outputPath, null);
    }

    /**
     * Compresses a file and returns both raw statistics and GUI-friendly Huffman metadata.
     */
    public CompressionReport compressWithReport(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        validatePaths(inputPath, outputPath);

        long startNanos = System.nanoTime();
        long originalSize = Files.size(inputPath);
        FrequencyTable table = analyze(inputPath, originalSize, listener);

        // The leaf table gives O(1) access from a byte value to its already-built Huffman code.
        HuffmanCode root = HuffmanCode.buildTree(table, createQueue());
        HuffmanCode[] leaves = root == null ? new HuffmanCode[256] : root.leaves();
        long theoreticalBitCount = totalBitCount(table, leaves);
        double entropy = table.entropy();
        double averageCodeLength = averageCodeLength(table, theoreticalBitCount);

        // The archive header depends on the selected mode, so we estimate both sizes before
        // writing anything and keep whichever representation is smaller.
        ArchiveHeader storedHeader = ArchiveHeader.stored(originalSize);
        long storedArchiveSize = storedHeader.sizeInBytes() + originalSize;

        ArchiveHeader huffmanHeader = null;
        long huffmanArchiveSize = Long.MAX_VALUE;
        if (table.distinctSymbolCount() > 0) {
            huffmanHeader = ArchiveHeader.huffman(originalSize, table.copyFrequencies());
            huffmanArchiveSize = huffmanHeader.sizeInBytes() + bytesForBits(theoreticalBitCount);
        }

        CompressionMode mode = shouldUseHuffman(table, huffmanArchiveSize, storedArchiveSize)
            ? CompressionMode.HUFFMAN
            : CompressionMode.STORED;
        ArchiveHeader header = mode == CompressionMode.HUFFMAN ? huffmanHeader : storedHeader;

        writeArchive(inputPath, outputPath, header, leaves, originalSize, table.distinctSymbolCount(), listener);

        long archiveSize = Files.size(outputPath);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        CompressionResult result = new CompressionResult(
            mode,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            table.distinctSymbolCount(),
            theoreticalBitCount,
            entropy,
            averageCodeLength,
            elapsedMillis
        );
        return new CompressionReport(result, buildSymbolInfos(table, leaves), buildTreeInfo(root, table.totalCount(), ""));
    }

    /**
     * Decompresses an archive without progress callbacks.
     *
     * @param inputPath archive path to decompress
     * @param outputPath restored output path
     * @return decompression statistics for the completed operation
     * @throws IOException if the archive cannot be read, the output cannot be written, or the
     *     archive is malformed
     */
    public DecompressionResult decompress(Path inputPath, Path outputPath) throws IOException {
        return decompressWithReport(inputPath, outputPath).result();
    }

    /**
     * Decompresses an archive and optionally reports progress snapshots to the caller.
     *
     * @param inputPath archive path to decompress
     * @param outputPath restored output path
     * @param listener optional progress callback, or {@code null} for no progress reporting
     * @return decompression statistics for the completed operation
     * @throws IOException if the archive cannot be read, the output cannot be written, or the
     *     archive is malformed
     */
    public DecompressionResult decompress(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        return decompressWithReport(inputPath, outputPath, listener).result();
    }

    /**
     * Decompresses an archive and returns both raw statistics and GUI-friendly Huffman metadata.
     */
    public DecompressionReport decompressWithReport(Path inputPath, Path outputPath) throws IOException {
        return decompressWithReport(inputPath, outputPath, null);
    }

    /**
     * Decompresses an archive and returns both raw statistics and GUI-friendly Huffman metadata.
     */
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

            if (header.mode() == CompressionMode.STORED) {
                copyExact(input, output, header.originalSize(), tracker);
            } else {
                FrequencyTable table = FrequencyTable.fromFrequencies(header.frequencies());
                HuffmanCode root = HuffmanCode.buildTree(table, createQueue());
                symbolInfos = buildSymbolInfos(table, root == null ? new HuffmanCode[256] : root.leaves());
                treeInfo = buildTreeInfo(root, table.totalCount(), "");
                decompressHuffman(input, output, table, root, header.originalSize(), tracker);
            }

            output.flush();
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            DecompressionResult result = new DecompressionResult(header.mode(), archiveSize, header.originalSize(), elapsedMillis);
            return new DecompressionReport(result, symbolInfos, treeInfo);
        }
    }

    /**
     * Reads the input once to build a frequency table for all byte values.
     */
    private FrequencyTable analyze(Path inputPath, long totalBytes, ProgressListener listener) throws IOException {
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

    /**
     * Returns whether the Huffman archive should be preferred over the stored representation.
     */
    private boolean shouldUseHuffman(FrequencyTable table, long huffmanArchiveSize, long storedArchiveSize) {
        return table.distinctSymbolCount() > 0 && huffmanArchiveSize < storedArchiveSize;
    }

    /**
     * Computes the total number of payload bits that would be produced by the current code table.
     */
    private long totalBitCount(FrequencyTable table, HuffmanCode[] leaves) {
        long bitCount = 0L;
        for (int symbol = 0; symbol < 256; symbol++) {
            long frequency = table.frequencyOf(symbol);
            if (frequency == 0L) continue;
            bitCount += frequency * leaves[symbol].depth();
        }
        return bitCount;
    }

    /**
     * Computes the average code length from the total payload bit count.
     */
    private double averageCodeLength(FrequencyTable table, long totalBitCount) {
        long totalCount = table.totalCount();
        if (totalCount == 0L) return 0.0;
        return totalBitCount / (double) totalCount;
    }

    /**
     * Converts the Huffman tree to a symbol table suitable for the GUI.
     */
    private List<HuffmanSymbolInfo> buildSymbolInfos(FrequencyTable table, HuffmanCode[] leaves) {
        if (table.distinctSymbolCount() == 0) {
            return List.of();
        }

        double totalCount = table.totalCount();
        List<HuffmanSymbolInfo> symbols = new ArrayList<>(table.distinctSymbolCount());
        for (int symbol = 0; symbol < 256; symbol++) {
            long frequency = table.frequencyOf(symbol);
            if (frequency == 0L) {
                continue;
            }
            symbols.add(new HuffmanSymbolInfo(
                symbol,
                frequency,
                frequency / totalCount,
                codeString(leaves[symbol] == null ? new byte[0] : leaves[symbol].code())
            ));
        }
        return List.copyOf(symbols);
    }

    /**
     * Converts the internal Huffman tree to an immutable view model for the GUI.
     */
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

    /**
     * Serializes a byte-array code to a human-readable bit string.
     */
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

    /**
     * Writes the chosen archive representation after the caller has already selected the mode.
     */
    private void writeArchive(Path inputPath,
                              Path outputPath,
                              ArchiveHeader header,
                              HuffmanCode[] leaves,
                              long originalSize,
                              int distinctSymbolCount,
                              ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput)) {

            header.write(output);

            if (header.mode() == CompressionMode.STORED) {
                writeStoredPayload(inputPath, output, originalSize, listener);
                return;
            }

            // Single-symbol Huffman archives need only the header; decompression reconstructs the
            // file by repeating the single stored symbol the required number of times.
            if (distinctSymbolCount <= 1) {
                new ProgressTracker(listener, ProgressPhase.COMPRESSING, originalSize).complete(originalSize);
                return;
            }

            writeHuffmanPayload(inputPath, output, leaves, originalSize, listener);
        }
    }

    /**
     * Copies the original file bytes verbatim after a stored-mode header.
     */
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

    /**
     * Encodes the file through the precomputed Huffman leaf table and writes the resulting bits.
     */
    private void writeHuffmanPayload(Path inputPath,
                                     OutputStream output,
                                     HuffmanCode[] leaves,
                                     long totalBytes,
                                     ProgressListener listener) throws IOException {
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, totalBytes);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE);
             BitOutputStream bitOutput = new BitOutputStream(output)) {

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

            tracker.complete(processedBytes);
        }
    }

    /**
     * Rebuilds the Huffman tree from the archive metadata and decodes the payload bit by bit.
     */
    private void decompressHuffman(DataInputStream input,
                                   OutputStream output,
                                   FrequencyTable table,
                                   HuffmanCode root,
                                   long originalSize,
                                   ProgressTracker tracker) throws IOException {
        if (originalSize == 0L) {
            tracker.complete(0L);
            return;
        }
        if (root == null) {
            tracker.complete(0L);
            return;
        }

        if (table.distinctSymbolCount() == 1) {
            writeRepeatedByte(output, table.singleSymbol(), originalSize, tracker);
            return;
        }

        BitInputStream bitInput = new BitInputStream(input);
        HuffmanCode currentNode = root;
        long restoredBytes = 0L;

        // In the normal case we descend one edge per bit until we hit a leaf, emit its symbol,
        // and restart from the root for the next symbol.
        while (restoredBytes < originalSize) {
            int bit = bitInput.readBit();
            if (bit < 0) throw new ArchiveFormatException("Final inesperat de la carrega Huffman.");

            currentNode = bit == 0 ? currentNode.min() : currentNode.max();
            if (currentNode == null) throw new ArchiveFormatException("Cami Huffman invalid dins la carrega.");
            if (currentNode.isLeaf()) {
                output.write(currentNode.symbol());
                restoredBytes++;
                tracker.update(restoredBytes);
                currentNode = root;
            }
        }

        tracker.complete(restoredBytes);
    }

    /**
     * Writes the same byte value repeatedly, used for one-symbol Huffman archives.
     */
    private void writeRepeatedByte(OutputStream output,
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
            tracker.update(written);
        }
        tracker.complete(written);
    }

    /**
     * Copies an exact number of bytes and fails if the underlying stream ends too early.
     */
    private void copyExact(InputStream input,
                           OutputStream output,
                           long expectedBytes,
                           ProgressTracker tracker) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copiedBytes = 0L;

        while (copiedBytes < expectedBytes) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, expectedBytes - copiedBytes));
            if (read < 0) throw new ArchiveFormatException("Final inesperat de la carrega emmagatzemada.");
            output.write(buffer, 0, read);
            copiedBytes += read;
            tracker.update(copiedBytes);
        }

        tracker.complete(copiedBytes);
    }

    /**
     * Converts a bit count to the number of whole bytes needed to store it.
     */
    private static long bytesForBits(long bitCount) {
        return (bitCount + 7L) / 8L;
    }

    /**
     * Creates the priority queue requested by the configured strategy.
     */
    private NodeQueue<HuffmanCode> createQueue() {
        if (priorityQueueStrategy == PriorityQueueStrategy.BINARY_HEAP) return new BinaryHeapNodeQueue<>();
        throw new IllegalArgumentException("Estrategia de cua no suportada: " + priorityQueueStrategy);
    }

    /**
     * Rejects in-place compression/decompression because the output would overwrite the input.
     */
    private static void validatePaths(Path inputPath, Path outputPath) {
        Path normalizedInput = inputPath.toAbsolutePath().normalize();
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (normalizedInput.equals(normalizedOutput)) {
            throw new IllegalArgumentException("Les rutes d'entrada i de sortida han de ser diferents.");
        }
    }
}
