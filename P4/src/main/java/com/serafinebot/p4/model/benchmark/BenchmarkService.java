package com.serafinebot.p4.model.benchmark;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.CompressionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Executes benchmarks over a directory of real corpus files.
 */
public final class BenchmarkService {

    /**
     * Executes the benchmark described by {@code config}.
     */
    public BenchmarkReport run(BenchmarkConfig config) throws IOException {
        return run(config, null);
    }

    /**
     * Executes the benchmark described by {@code config} and optionally reports progress.
     */
    public BenchmarkReport run(BenchmarkConfig config, BenchmarkProgressListener progressListener) throws IOException {
        List<BenchmarkPoint> points = new ArrayList<>();
        List<BenchmarkModePoint> modePoints = new ArrayList<>();
        List<CorpusInput> corpusInputs = loadCorpusInputs(config.corpusDirectory());
        int totalSteps = corpusInputs.size() * PriorityQueueStrategy.values().length * config.repetitions();
        int completedSteps = 0;
        Path tempDirectory = Files.createTempDirectory("p4-benchmark-");

        try {
            for (int inputIndex = 0; inputIndex < corpusInputs.size(); inputIndex++) {
                CorpusInput input = corpusInputs.get(inputIndex);
                modePoints.addAll(measureModeCompression(input, inputIndex, tempDirectory, config.repetitions()));

                for (PriorityQueueStrategy strategy : PriorityQueueStrategy.values()) {
                    double totalCompressionMillis = 0.0;
                    double totalDecompressionMillis = 0.0;
                    double totalEntropy = 0.0;
                    double totalAverageCodeLength = 0.0;
                    double totalCompressionPercentage = 0.0;

                    for (int repetition = 0; repetition < config.repetitions(); repetition++) {
                        Path archivePath = tempDirectory.resolve("archive-" + inputIndex + '-' + strategy.name() + '-' + repetition + ".hff");
                        Path restoredPath = tempDirectory.resolve("restored-" + inputIndex + '-' + strategy.name() + '-' + repetition + ".bin");

                        HuffmanCodec codec = new HuffmanCodec(strategy);

                        long startCompression = System.nanoTime();
                        CompressionResult compressionResult = codec.compress(input.path(), archivePath);
                        totalCompressionMillis += nanosToMillis(System.nanoTime() - startCompression);

                        long startDecompression = System.nanoTime();
                        codec.decompress(archivePath, restoredPath);
                        totalDecompressionMillis += nanosToMillis(System.nanoTime() - startDecompression);

                        if (Files.mismatch(input.path(), restoredPath) != -1L) {
                            throw new IOException("El benchmark ha produït una descompressio incorrecta per al fitxer " + input.sourceName() + '.');
                        }

                        totalEntropy += compressionResult.entropy();
                        totalAverageCodeLength += compressionResult.averageHuffmanCodeLength();
                        totalCompressionPercentage += compressionResult.compressionPercentage();

                        completedSteps++;
                        reportProgress(progressListener, completedSteps, totalSteps, input.sourceName(), strategy, input.sizeBytes());

                        Files.deleteIfExists(archivePath);
                        Files.deleteIfExists(restoredPath);
                    }

                    points.add(new BenchmarkPoint(
                        input.sourceName(),
                        strategy,
                        input.sizeBytes(),
                        totalCompressionMillis / config.repetitions(),
                        totalDecompressionMillis / config.repetitions(),
                        totalEntropy / config.repetitions(),
                        totalAverageCodeLength / config.repetitions(),
                        totalCompressionPercentage / config.repetitions()
                    ));
                }
            }
        } finally {
            try (var walk = Files.walk(tempDirectory)) {
                walk.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best effort cleanup.
                    }
                });
            }
        }

        return new BenchmarkReport(config, points, modePoints);
    }

    private List<BenchmarkModePoint> measureModeCompression(CorpusInput input,
                                                            int inputIndex,
                                                            Path tempDirectory,
                                                            int repetitions) throws IOException {
        List<BenchmarkModePoint> points = new ArrayList<>();
        for (CompressionMode mode : List.of(CompressionMode.HUFFMAN_1_BYTE, CompressionMode.HUFFMAN_2_BYTE, CompressionMode.HUFFMAN_BLOCK)) {
            double totalCompressionPercentage = 0.0;
            for (int repetition = 0; repetition < repetitions; repetition++) {
                Path archivePath = tempDirectory.resolve("mode-archive-" + inputIndex + '-' + mode.name() + '-' + repetition + ".hff");
                CompressionResult result = new HuffmanCodec(PriorityQueueStrategy.BINARY_HEAP, mode).compress(input.path(), archivePath);
                totalCompressionPercentage += result.compressionPercentage();
                Files.deleteIfExists(archivePath);
            }

            points.add(new BenchmarkModePoint(
                input.sourceName(),
                mode,
                input.sizeBytes(),
                totalCompressionPercentage / repetitions
            ));
        }
        return points;
    }

    private void reportProgress(BenchmarkProgressListener listener,
                                int completedSteps,
                                int totalSteps,
                                String sourceName,
                                PriorityQueueStrategy strategy,
                                long sizeBytes) {
        if (listener == null) {
            return;
        }
        listener.onProgress(new BenchmarkProgressSnapshot(completedSteps, totalSteps, sourceName, strategy, sizeBytes));
    }

    private List<CorpusInput> loadCorpusInputs(Path corpusDirectory) throws IOException {
        Path normalizedDirectory = corpusDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedDirectory)) {
            throw new IOException("El directori del corpus no existeix o no es un directori: " + normalizedDirectory);
        }

        List<CorpusInput> inputs = new ArrayList<>();
        try (var walk = Files.walk(normalizedDirectory)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                inputs.add(new CorpusInput(path, path.getFileName().toString(), Files.size(path)));
            }
        }

        if (inputs.isEmpty()) {
            throw new IOException("No s'ha trobat cap fitxer regular dins el directori del corpus: " + normalizedDirectory);
        }

        inputs.sort(Comparator.comparingLong(CorpusInput::sizeBytes).thenComparing(CorpusInput::sourceName));
        return List.copyOf(inputs);
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record CorpusInput(Path path, String sourceName, long sizeBytes) {
    }
}
