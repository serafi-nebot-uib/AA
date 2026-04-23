package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.progress.ProgressListener;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.info.CompressionInfo;
import com.serafinebot.p4.model.info.CompressionStats;
import com.serafinebot.p4.model.info.DecompressionInfo;
import com.serafinebot.p4.model.info.DecompressionStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Public model service for compressing and decompressing files with the supported Huffman modes.
 *
 * <p>This class is intentionally a facade. It owns the stable API used by the controller, CLI, and
 * benchmarks, while mode-specific analysis, archive I/O, mode selection, and info construction
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
        return compressWithInfo(inputPath, outputPath).stats();
    }

    public CompressionStats compress(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        return compressWithInfo(inputPath, outputPath, listener).stats();
    }

    public CompressionInfo compressWithInfo(Path inputPath, Path outputPath) throws IOException {
        return compressWithInfo(inputPath, outputPath, null);
    }

    public CompressionInfo compressWithInfo(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        validatePaths(inputPath, outputPath);

        long startNanos = System.nanoTime();
        long originalSize = Files.size(inputPath);

        HuffmanWholeFileAnalyzer wholeFileAnalyzer = new HuffmanWholeFileAnalyzer(priorityQueueStrategy);
        HuffmanBlockAnalyzer blockAnalyzer = new HuffmanBlockAnalyzer(
            priorityQueueStrategy,
            requestedCompressionMode.allowedBlockHuffmanModes()
        );

        // The compressor estimates every supported Huffman representation before writing. Whole-file
        // STORED is intentionally excluded; if no Huffman archive is at most as small as the input,
        // compression fails without producing an oversized file.
        WholePlan bytePlan = wholeFileAnalyzer.analyzeByte(inputPath, originalSize, listener);
        WholePlan wordPlan = wholeFileAnalyzer.analyzeWord(inputPath, originalSize, listener);
        BlockPlan blockPlan = blockAnalyzer.analyze(inputPath, originalSize, listener);

        HuffmanArchive archive = HuffmanModeSelector.select(
            requestedCompressionMode,
            bytePlan,
            wordPlan,
            blockPlan
        );
        if (archive.estimatedArchiveSize() > originalSize) {
            throw new IOException("La compressio generaria un arxiu mes gran que l'original; no s'ha escrit cap fitxer.");
        }

        HuffmanArchiveWriter.write(inputPath, outputPath, archive, listener);

        long archiveSize = Files.size(outputPath);
        if (archiveSize > originalSize) {
            Files.deleteIfExists(outputPath);
            throw new IOException("La compressio ha generat un arxiu mes gran que l'original; s'ha eliminat el fitxer de sortida.");
        }

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        long treeBuildMillis = (wholeFileAnalyzer.treeBuildNanos() + blockAnalyzer.treeBuildNanos()) / 1_000_000L;
        return HuffmanInfoFactory.compressionInfo(
            archive.mode(),
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
        return decompressWithInfo(inputPath, outputPath).stats();
    }

    public DecompressionStats decompress(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        return decompressWithInfo(inputPath, outputPath, listener).stats();
    }

    public DecompressionInfo decompressWithInfo(Path inputPath, Path outputPath) throws IOException {
        return decompressWithInfo(inputPath, outputPath, null);
    }

    public DecompressionInfo decompressWithInfo(Path inputPath, Path outputPath, ProgressListener listener) throws IOException {
        validatePaths(inputPath, outputPath);

        long startNanos = System.nanoTime();
        long archiveSize = Files.size(inputPath);

        HuffmanArchiveReader reader = new HuffmanArchiveReader(priorityQueueStrategy);
        HuffmanArchive archive = reader.read(inputPath, outputPath, listener);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        DecompressionStats stats = new DecompressionStats(
            archive.mode(),
            archiveSize,
            archive.originalSize(),
            elapsedMillis,
            reader.treeBuildMillis()
        );
        return new DecompressionInfo(stats, archive.symbols(), archive.tree(), archive.blocks());
    }

    private static void validatePaths(Path inputPath, Path outputPath) {
        Path normalizedInput = inputPath.toAbsolutePath().normalize();
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (normalizedInput.equals(normalizedOutput)) {
            throw new IllegalArgumentException("Les rutes d'entrada i de sortida han de ser diferents.");
        }
    }
}
