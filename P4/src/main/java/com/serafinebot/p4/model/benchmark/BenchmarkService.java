package com.serafinebot.p4.model.benchmark;

import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.CompressionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Executes deterministic synthetic benchmarks over all configured queue strategies.
 */
public final class BenchmarkService {

    private static final byte[] TEXT_ALPHABET = "eeeeeeeeeeeeaaaaaaaaiiiiiiiiooooooosssssrrrnnnntttllccuupmdh,.;:-_ ()".getBytes();

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
        int[] sizes = sizes(config);
        int totalSteps = BenchmarkProfile.values().length * sizes.length * PriorityQueueStrategy.values().length * config.repetitions();
        int completedSteps = 0;
        Path tempDirectory = Files.createTempDirectory("p4-benchmark-");

        try {
            for (BenchmarkProfile profile : BenchmarkProfile.values()) {
                for (int size : sizes) {
                    byte[] data = generate(profile, size, 73L + profile.ordinal() * 997L + size);
                    Path inputPath = tempDirectory.resolve("input-" + profile.ordinal() + '-' + size + ".bin");
                    Files.write(inputPath, data);

                    for (PriorityQueueStrategy strategy : PriorityQueueStrategy.values()) {
                        double totalCompressionMillis = 0.0;
                        double totalDecompressionMillis = 0.0;
                        double totalEntropy = 0.0;
                        double totalAverageCodeLength = 0.0;
                        double totalCompressionPercentage = 0.0;

                        for (int repetition = 0; repetition < config.repetitions(); repetition++) {
                            Path archivePath = tempDirectory.resolve("archive-" + profile.ordinal() + '-' + size + '-' + strategy.name() + '-' + repetition + ".hff");
                            Path restoredPath = tempDirectory.resolve("restored-" + profile.ordinal() + '-' + size + '-' + strategy.name() + '-' + repetition + ".bin");

                            HuffmanCodec codec = new HuffmanCodec(strategy);

                            long startCompression = System.nanoTime();
                            CompressionResult compressionResult = codec.compress(inputPath, archivePath);
                            totalCompressionMillis += nanosToMillis(System.nanoTime() - startCompression);

                            long startDecompression = System.nanoTime();
                            codec.decompress(archivePath, restoredPath);
                            totalDecompressionMillis += nanosToMillis(System.nanoTime() - startDecompression);

                            if (!Arrays.equals(data, Files.readAllBytes(restoredPath))) {
                                throw new IOException("El benchmark ha produït una descompressio incorrecta.");
                            }

                            totalEntropy += compressionResult.entropy();
                            totalAverageCodeLength += compressionResult.averageHuffmanCodeLength();
                            totalCompressionPercentage += compressionResult.compressionPercentage();

                            completedSteps++;
                            reportProgress(progressListener, completedSteps, totalSteps, profile, strategy, size);

                            Files.deleteIfExists(archivePath);
                            Files.deleteIfExists(restoredPath);
                        }

                        points.add(new BenchmarkPoint(
                            profile,
                            strategy,
                            size,
                            totalCompressionMillis / config.repetitions(),
                            totalDecompressionMillis / config.repetitions(),
                            totalEntropy / config.repetitions(),
                            totalAverageCodeLength / config.repetitions(),
                            totalCompressionPercentage / config.repetitions()
                        ));
                    }

                    Files.deleteIfExists(inputPath);
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

        reportProgress(progressListener, totalSteps, totalSteps, BenchmarkProfile.RANDOM_BYTES, PriorityQueueStrategy.BINARY_HEAP, config.maxSizeBytes());
        return new BenchmarkReport(config, points);
    }

    private void reportProgress(BenchmarkProgressListener listener,
                                int completedSteps,
                                int totalSteps,
                                BenchmarkProfile profile,
                                PriorityQueueStrategy strategy,
                                int sizeBytes) {
        if (listener == null) {
            return;
        }
        listener.onProgress(new BenchmarkProgressSnapshot(completedSteps, totalSteps, profile, strategy, sizeBytes));
    }

    private int[] sizes(BenchmarkConfig config) {
        if (config.pointCount() == 1) {
            return new int[] {config.minSizeBytes()};
        }

        int[] sizes = new int[config.pointCount()];
        double step = (config.maxSizeBytes() - config.minSizeBytes()) / (double) (config.pointCount() - 1);
        int previous = 0;
        for (int i = 0; i < sizes.length; i++) {
            int candidate = (int) Math.round(config.minSizeBytes() + step * i);
            if (i > 0 && candidate <= previous) {
                candidate = previous + 1;
            }
            sizes[i] = Math.min(candidate, config.maxSizeBytes());
            previous = sizes[i];
        }
        sizes[sizes.length - 1] = config.maxSizeBytes();
        return sizes;
    }

    private byte[] generate(BenchmarkProfile profile, int size, long seed) {
        Random random = new Random(seed);
        byte[] data = new byte[size];

        if (profile == BenchmarkProfile.RANDOM_BYTES) {
            random.nextBytes(data);
            return data;
        }

        if (profile == BenchmarkProfile.LOW_ENTROPY) {
            for (int i = 0; i < data.length; i++) {
                double sample = random.nextDouble();
                if (sample < 0.72) {
                    data[i] = 'A';
                } else if (sample < 0.86) {
                    data[i] = 'B';
                } else if (sample < 0.95) {
                    data[i] = 'C';
                } else {
                    data[i] = (byte) ('0' + random.nextInt(10));
                }
            }
            return data;
        }

        for (int i = 0; i < data.length; i++) {
            data[i] = TEXT_ALPHABET[random.nextInt(TEXT_ALPHABET.length)];
        }
        return data;
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
