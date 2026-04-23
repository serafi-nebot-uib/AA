package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.progress.ProgressListener;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.CompressionReport;
import com.serafinebot.p4.model.report.CompressionStats;
import com.serafinebot.p4.model.report.DecompressionReport;
import com.serafinebot.p4.model.report.DecompressionStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Public model service for compressing and decompressing files with the supported Huffman modes.
 *
 * <p>This class is intentionally a facade. It owns the stable API used by the controller, CLI, and
 * benchmarks, while mode-specific analysis, archive I/O, mode selection, and report construction
 * live in package-private collaborators.</p>
 */
public class HuffmanCodec {

    private final PriorityQueueStrategy priorityQueueStrategy;
    private final CompressionMode requestedCompressionMode;

    public HuffmanCodec() {
        this(PriorityQueueStrategy.BINARY_HEAP, CompressionMode.AUTO);
    }

    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy) {
        this(priorityQueueStrategy, CompressionMode.AUTO);
    }

    public HuffmanCodec(PriorityQueueStrategy priorityQueueStrategy, CompressionMode requestedCompressionMode) {
        this.priorityQueueStrategy = priorityQueueStrategy;
        this.requestedCompressionMode = requestedCompressionMode == null ? CompressionMode.AUTO : requestedCompressionMode;
    }

    public CompressionStats compress(Path inputPath, Path outputPath) throws IOException {
        return compressWithReport(inputPath, outputPath).result();
    }

    public CompressionStats compress(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        return compressWithReport(inputPath, outputPath, listener).result();
    }

    public CompressionReport compressWithReport(Path inputPath, Path outputPath) throws IOException {
        return compressWithReport(inputPath, outputPath, null);
    }

    public CompressionReport compressWithReport(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        validatePaths(inputPath, outputPath);

        long startNanos = System.nanoTime();
        long originalSize = Files.size(inputPath);

        HuffmanWholeFileAnalyzer wholeFileAnalyzer = new HuffmanWholeFileAnalyzer(priorityQueueStrategy);
        HuffmanBlockAnalyzer blockAnalyzer = new HuffmanBlockAnalyzer(
            priorityQueueStrategy,
            requestedCompressionMode.allowedBlockHuffmanModes()
        );

        // The compressor estimates every supported representation before writing. STORED is always
        // the fallback candidate and Huffman modes must beat it to be selected automatically.
        WholePlan bytePlan = wholeFileAnalyzer.analyzeByte(inputPath, originalSize, listener);
        WholePlan wordPlan = wholeFileAnalyzer.analyzeWord(inputPath, originalSize, listener);
        BlockPlan blockPlan = blockAnalyzer.analyze(inputPath, originalSize, listener);
        long storedArchiveSize = ArchiveHeader.stored(originalSize).sizeInBytes() + originalSize;

        CompressionMode selectedMode = HuffmanModeSelector.select(
            requestedCompressionMode,
            storedArchiveSize,
            bytePlan,
            wordPlan,
            blockPlan
        );
        writeSelectedPlan(inputPath, outputPath, originalSize, listener, selectedMode, bytePlan, wordPlan, blockPlan);

        long archiveSize = Files.size(outputPath);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        long treeBuildMillis = (wholeFileAnalyzer.treeBuildNanos() + blockAnalyzer.treeBuildNanos()) / 1_000_000L;
        return HuffmanReport.compressionReport(
            selectedMode,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            elapsedMillis,
            treeBuildMillis,
            bytePlan,
            wordPlan,
            blockPlan
        );
    }

    public DecompressionStats decompress(Path inputPath, Path outputPath) throws IOException {
        return decompressWithReport(inputPath, outputPath).result();
    }

    public DecompressionStats decompress(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        return decompressWithReport(inputPath, outputPath, listener).result();
    }

    public DecompressionReport decompressWithReport(Path inputPath, Path outputPath) throws IOException {
        return decompressWithReport(inputPath, outputPath, null);
    }

    public DecompressionReport decompressWithReport(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        validatePaths(inputPath, outputPath);

        long startNanos = System.nanoTime();
        long archiveSize = Files.size(inputPath);

        HuffmanArchiveReader reader = new HuffmanArchiveReader(priorityQueueStrategy);
        DecodedArchive archive = reader.read(inputPath, outputPath, listener);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        DecompressionStats result = new DecompressionStats(
            archive.mode(),
            archiveSize,
            archive.originalSize(),
            elapsedMillis,
            reader.treeBuildMillis()
        );
        return new DecompressionReport(result, archive.symbols(), archive.tree(), archive.blocks());
    }

    private void writeSelectedPlan(Path inputPath,
                                   Path outputPath,
                                   long originalSize,
                                   ProgressListener listener,
                                   CompressionMode selectedMode,
                                   WholePlan bytePlan,
                                   WholePlan wordPlan,
                                   BlockPlan blockPlan) throws IOException {
        switch (selectedMode) {
            case STORED -> HuffmanArchiveWriter.writeStored(inputPath, outputPath, originalSize, listener);
            case HUFFMAN_1_BYTE -> HuffmanArchiveWriter.writeByte(inputPath, outputPath, bytePlan, listener);
            case HUFFMAN_2_BYTE -> HuffmanArchiveWriter.writeWord(inputPath, outputPath, wordPlan, listener);
            case HUFFMAN_BLOCK -> HuffmanArchiveWriter.writeBlock(inputPath, outputPath, blockPlan, listener);
            default -> throw new IllegalStateException("Mode d'arxiu seleccionat no valid: " + selectedMode);
        }
    }

    private static void validatePaths(Path inputPath, Path outputPath) {
        Path normalizedInput = inputPath.toAbsolutePath().normalize();
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (normalizedInput.equals(normalizedOutput)) {
            throw new IllegalArgumentException("Les rutes d'entrada i de sortida han de ser diferents.");
        }
    }
}
