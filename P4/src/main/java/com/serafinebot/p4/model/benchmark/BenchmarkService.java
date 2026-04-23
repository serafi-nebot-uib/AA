package com.serafinebot.p4.model.benchmark;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.info.CompressionStats;
import com.serafinebot.p4.model.info.DecompressionStats;

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
        List<QueueBenchmarkPoint> queuePoints = new ArrayList<>();
        List<CompressionModeBenchmarkPoint> modePoints = new ArrayList<>();
        List<CorpusInput> corpusInputs = loadCorpusInputs(config.corpusDirectory());
        int totalSteps = corpusInputs.size() * PriorityQueueStrategy.values().length * config.repetitions();
        int completedSteps = 0;
        Path tempDirectory = Files.createTempDirectory("p4-benchmark-");

        try {
            for (int inputIndex = 0; inputIndex < corpusInputs.size(); inputIndex++) {
                CorpusInput input = corpusInputs.get(inputIndex);
                modePoints.addAll(measureModeCompression(input, inputIndex, tempDirectory, config.repetitions()));

                for (PriorityQueueStrategy strategy : PriorityQueueStrategy.values()) {
                    double totalCompressionTreeBuildMillis = 0.0;
                    double totalDecompressionTreeBuildMillis = 0.0;
                    double totalEntropy = 0.0;
                    double totalAverageCodeLength = 0.0;
                    double totalCompressionPercentage = 0.0;

                    for (int repetition = 0; repetition < config.repetitions(); repetition++) {
                        Path archivePath = tempDirectory.resolve("archive-" + inputIndex + '-' + strategy.name() + '-' + repetition + ".hff");
                        Path restoredPath = tempDirectory.resolve("restored-" + inputIndex + '-' + strategy.name() + '-' + repetition + ".bin");

                        HuffmanCodec codec = new HuffmanCodec(strategy);

                        CompressionStats compressionStats = codec.compress(input.path(), archivePath);
                        totalCompressionTreeBuildMillis += compressionStats.treeBuildMillis();

                        DecompressionStats decompressionStats = codec.decompress(archivePath, restoredPath);
                        totalDecompressionTreeBuildMillis += decompressionStats.treeBuildMillis();

                        if (Files.mismatch(input.path(), restoredPath) != -1L) {
                            throw new IOException("El benchmark ha produït una descompressio incorrecta per al fitxer " + input.sourceName() + '.');
                        }

                        totalEntropy += compressionStats.entropy();
                        totalAverageCodeLength += compressionStats.averageHuffmanCodeLength();
                        totalCompressionPercentage += compressionStats.compressionPercentage();

                        completedSteps++;
                        reportProgress(progressListener, completedSteps, totalSteps, input.sourceName(), strategy, input.sizeBytes());

                        Files.deleteIfExists(archivePath);
                        Files.deleteIfExists(restoredPath);
                    }

                    queuePoints.add(new QueueBenchmarkPoint(
                        input.sourceName(),
                        strategy,
                        input.sizeBytes(),
                        totalCompressionTreeBuildMillis / config.repetitions(),
                        totalDecompressionTreeBuildMillis / config.repetitions(),
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

        return new BenchmarkReport(config, queuePoints, modePoints);
    }

    private List<CompressionModeBenchmarkPoint> measureModeCompression(CorpusInput input,
                                                            int inputIndex,
                                                            Path tempDirectory,
                                                            int repetitions) throws IOException {
        List<CompressionModeBenchmarkPoint> modePoints = new ArrayList<>();
        for (CompressionMode mode : CompressionMode.benchmarkModes()) {
            double totalCompressionPercentage = 0.0;
            double totalCompressionMillis = 0.0;
            double totalDecompressionMillis = 0.0;
            for (int repetition = 0; repetition < repetitions; repetition++) {
                Path archivePath = tempDirectory.resolve("mode-archive-" + inputIndex + '-' + mode.name() + '-' + repetition + ".hff");
                Path restoredPath = tempDirectory.resolve("mode-restored-" + inputIndex + '-' + mode.name() + '-' + repetition + ".bin");
                HuffmanCodec codec = new HuffmanCodec(PriorityQueueStrategy.BINARY_HEAP, mode);

                long startCompression = System.nanoTime();
                CompressionStats compressionStats = codec.compress(input.path(), archivePath);
                totalCompressionMillis += nanosToMillis(System.nanoTime() - startCompression);

                long startDecompression = System.nanoTime();
                codec.decompress(archivePath, restoredPath);
                totalDecompressionMillis += nanosToMillis(System.nanoTime() - startDecompression);

                totalCompressionPercentage += compressionStats.compressionPercentage();
                Files.deleteIfExists(archivePath);
                Files.deleteIfExists(restoredPath);
            }

            modePoints.add(new CompressionModeBenchmarkPoint(
                input.sourceName(),
                mode,
                input.sizeBytes(),
                totalCompressionPercentage / repetitions,
                totalCompressionMillis / repetitions,
                totalDecompressionMillis / repetitions
            ));
        }
        return modePoints;
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
                String name = path.getFileName().toString();
                if (name.toLowerCase().endsWith(".hff")) {
                    continue;
                }
                inputs.add(new CorpusInput(path, name, Files.size(path)));
            }
        }

        if (inputs.isEmpty()) {
            throw new IOException("No s'ha trobat cap fitxer regular (no .hff) dins el directori: " + normalizedDirectory);
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
