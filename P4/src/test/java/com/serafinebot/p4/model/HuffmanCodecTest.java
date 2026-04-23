package com.serafinebot.p4.model;

import com.serafinebot.p4.model.archive.ArchiveFormatException;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.benchmark.BenchmarkConfig;
import com.serafinebot.p4.model.benchmark.BenchmarkCsvWriter;
import com.serafinebot.p4.model.benchmark.CompressionModeBenchmarkPoint;
import com.serafinebot.p4.model.benchmark.QueueBenchmarkPoint;
import com.serafinebot.p4.model.benchmark.BenchmarkProgressSnapshot;
import com.serafinebot.p4.model.benchmark.BenchmarkReport;
import com.serafinebot.p4.model.benchmark.BenchmarkService;
import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressSnapshot;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.CompressionReport;
import com.serafinebot.p4.model.report.CompressionStats;
import com.serafinebot.p4.model.report.DecompressionStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuffmanCodecTest {

    private static final int BLOCK_TEST_SIZE = 4096;

    @TempDir
    Path tempDir;

    private final HuffmanCodec codec = new HuffmanCodec();

    @Test
    void roundTripEmptyFile() throws IOException {
        RoundTrip roundTrip = roundTrip("empty", new byte[0]);
        byte[] archive = Files.readAllBytes(roundTrip.archivePath);

        assertEquals(CompressionMode.STORED, roundTrip.compressionStats.mode());
        assertEquals(0L, roundTrip.compressionStats.originalSize());
        assertEquals(13, archive.length);
        assertEquals(CompressionMode.STORED.id(), archive[4]);
        assertArrayEquals(new byte[0], Files.readAllBytes(roundTrip.restoredPath));
    }

    @Test
    void roundTripRepeatedByteFileUsesHuffman() throws IOException {
        byte[] data = new byte[4096];
        Arrays.fill(data, (byte) 0x5A);

        RoundTrip roundTrip = roundTrip("repeated", data);
        byte[] archive = Files.readAllBytes(roundTrip.archivePath);

        assertEquals(CompressionMode.HUFFMAN_1_BYTE, roundTrip.compressionStats.mode());
        assertEquals(1, roundTrip.compressionStats.distinctSymbolCount());
        assertEquals(0.0, roundTrip.compressionStats.entropy(), 1.0e-9);
        assertEquals(0.0, roundTrip.compressionStats.averageHuffmanCodeLength(), 1.0e-9);
        assertEquals(CompressionMode.HUFFMAN_1_BYTE.id(), archive[4]);
    }

    @Test
    void roundTripAllByteValues() throws IOException {
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        RoundTrip roundTrip = roundTrip("all-bytes", data);

        assertTrue(roundTrip.compressionStats.distinctSymbolCount() >= 128);
    }

    @Test
    void roundTripTextData() throws IOException {
        String text = "Huffman coding works best when symbols repeat. ".repeat(200);
        byte[] data = text.getBytes(StandardCharsets.UTF_8);

        RoundTrip roundTrip = roundTrip("text", data);

        assertNotNull(roundTrip.compressionStats.mode());
        assertTrue(roundTrip.compressionStats.averageHuffmanCodeLength() >= roundTrip.compressionStats.entropy());
        assertTrue(roundTrip.compressionStats.averageHuffmanCodeLength() < roundTrip.compressionStats.entropy() + 1.0);
    }

    @Test
    void compressionReportIncludesTreeAndSymbolTable() throws IOException {
        byte[] data = "banana bandana".getBytes(StandardCharsets.UTF_8);
        Path inputPath = writeInput("report-input", data);
        Path archivePath = tempDir.resolve("report.hff");

        CompressionReport report = codec.compressWithReport(inputPath, archivePath);

        assertFalse(report.symbols().isEmpty());
        assertTrue(report.symbols().stream().anyMatch(symbol -> symbol.symbol() == 'a' && symbol.probability() > 0.0 && !symbol.code().isEmpty()));
        assertNotNull(report.tree());
        assertEquals(data.length, report.tree().frequency());
        assertEquals(1.0, report.tree().probability(), 1.0e-9);
        assertEquals("", report.tree().code());
        assertTrue(report.tree().zeroChild() == null || report.tree().zeroChild().code().startsWith("0"));
        assertTrue(report.tree().oneChild() == null || report.tree().oneChild().code().startsWith("1"));
    }

    @Test
    void allQueueStrategiesProduceEquivalentArchives() throws IOException {
        byte[] data = "queue strategy comparison ".repeat(256).getBytes(StandardCharsets.UTF_8);
        Path inputPath = writeInput("all-queues", data);

        byte[] referenceArchive = null;
        for (PriorityQueueStrategy strategy : PriorityQueueStrategy.values()) {
            Path archivePath = tempDir.resolve("queue-" + strategy.name() + ".hff");
            Path restoredPath = tempDir.resolve("queue-" + strategy.name() + ".bin");

            HuffmanCodec strategyCodec = new HuffmanCodec(strategy);
            strategyCodec.compress(inputPath, archivePath);
            strategyCodec.decompress(archivePath, restoredPath);

            assertArrayEquals(data, Files.readAllBytes(restoredPath));

            byte[] archive = Files.readAllBytes(archivePath);
            if (referenceArchive == null) {
                referenceArchive = archive;
            } else {
                assertArrayEquals(referenceArchive, archive);
            }
        }
    }

    @Test
    void benchmarkServiceProducesPointsForAllStrategiesAndModeVariants() throws IOException {
        Path corpusDirectory = createBenchmarkCorpus();

        BenchmarkReport report = new BenchmarkService().run(new BenchmarkConfig(corpusDirectory, 1));

        assertEquals(2 * PriorityQueueStrategy.values().length, report.queuePoints().size());
        assertEquals(2 * CompressionMode.benchmarkModes().size(), report.modePoints().size());
        assertTrue(report.queuePoints().stream().allMatch(point -> point.sizeBytes() > 0L));
        assertTrue(report.modePoints().stream().allMatch(point -> point.sizeBytes() > 0L));
    }

    @Test
    void benchmarkCsvWriterProducesHeaderAndRows() {
        BenchmarkReport report = new BenchmarkReport(
            new BenchmarkConfig(tempDir, 1),
            List.of(new QueueBenchmarkPoint(
                "sample.txt",
                PriorityQueueStrategy.BINARY_HEAP,
                128L,
                1.25,
                0.75,
                3.5,
                4.0,
                12.5
            )),
            List.of(new CompressionModeBenchmarkPoint(
                "sample.txt",
                CompressionMode.HUFFMAN_1_BYTE,
                128L,
                12.5,
                2.0,
                1.0
            ))
        );

        String csv = BenchmarkCsvWriter.toCsv(report);

        assertTrue(csv.contains("source_name,strategy,size_bytes"));
        assertTrue(csv.contains("sample.txt,BINARY_HEAP,128"));
        assertTrue(csv.contains("source_name,mode,size_bytes"));
        assertTrue(csv.contains("sample.txt,HUFFMAN_1_BYTE,128"));
    }

    @Test
    void benchmarkServiceReportsProgress() throws IOException {
        Path corpusDirectory = createBenchmarkCorpus();
        List<BenchmarkProgressSnapshot> snapshots = new ArrayList<>();

        new BenchmarkService().run(new BenchmarkConfig(corpusDirectory, 1), snapshots::add);

        int expectedSteps = 2 * PriorityQueueStrategy.values().length;
        assertEquals(expectedSteps, snapshots.size());
        assertEquals(expectedSteps, snapshots.get(snapshots.size() - 1).completedSteps());
        assertEquals(1.0, snapshots.get(snapshots.size() - 1).completion(), 1.0e-9);
    }

    @Test
    void codecChoosesTwoByteStrategyWhenRepeatedPairsDominate() throws IOException {
        byte[] data = "AB".repeat(4096).getBytes(StandardCharsets.UTF_8);
        Path inputPath = writeInput("two-byte-strategy", data);
        Path archivePath = tempDir.resolve("two-byte-strategy.hff");

        CompressionStats result = codec.compress(inputPath, archivePath);

        assertEquals(CompressionMode.HUFFMAN_2_BYTE, result.mode());
    }

    @Test
    void blockModeRoundTripsWithTwoByteBlocks() throws IOException {
        int blockBytes = 1 << 17;
        byte[] first = new byte[blockBytes];
        byte[] second = new byte[blockBytes + 1];
        Random random = new Random(424242L);
        for (int i = 0; i < first.length; i += 2) {
            int symbol = random.nextInt(64);
            first[i] = (byte) ((symbol >>> 4) & 0x0F);
            first[i + 1] = (byte) (symbol & 0x0F);
        }
        for (int i = 0; i < second.length - 1; i += 2) {
            int symbol = random.nextInt(64) + 128;
            second[i] = (byte) ((symbol >>> 4) & 0x0F);
            second[i + 1] = (byte) (symbol & 0x0F);
        }
        second[second.length - 1] = (byte) 0x7F;

        byte[] data = new byte[first.length + second.length];
        System.arraycopy(first, 0, data, 0, first.length);
        System.arraycopy(second, 0, data, first.length, second.length);

        HuffmanCodec blockCodec = new HuffmanCodec(PriorityQueueStrategy.BINARY_HEAP, CompressionMode.HUFFMAN_BLOCK);
        Path inputPath = writeInput("two-byte-block", data);
        Path archivePath = tempDir.resolve("two-byte-block.hff");
        Path restoredPath = tempDir.resolve("two-byte-block-restored.bin");

        CompressionStats compressionStats = blockCodec.compress(inputPath, archivePath);
        DecompressionStats decompressionStats = blockCodec.decompress(archivePath, restoredPath);

        assertEquals(CompressionMode.HUFFMAN_BLOCK, compressionStats.mode());
        assertEquals(CompressionMode.HUFFMAN_BLOCK, decompressionStats.mode());
        assertArrayEquals(data, Files.readAllBytes(restoredPath));
    }

    @Test
    void requestedBlockSubModesRestrictBlockEncodingChoices() throws IOException {
        byte[] data = "AB".repeat(8192).getBytes(StandardCharsets.UTF_8);
        Path inputPath = writeInput("restricted-block-modes", data);

        CompressionReport byteOnlyReport = new HuffmanCodec(PriorityQueueStrategy.BINARY_HEAP, CompressionMode.HUFFMAN_BLOCK_1_BYTE)
            .compressWithReport(inputPath, tempDir.resolve("restricted-byte.hff"));
        CompressionReport wordOnlyReport = new HuffmanCodec(PriorityQueueStrategy.BINARY_HEAP, CompressionMode.HUFFMAN_BLOCK_2_BYTE)
            .compressWithReport(inputPath, tempDir.resolve("restricted-word.hff"));

        assertEquals(CompressionMode.HUFFMAN_BLOCK, byteOnlyReport.result().mode());
        assertEquals(CompressionMode.HUFFMAN_BLOCK, wordOnlyReport.result().mode());
        assertTrue(byteOnlyReport.blocks().stream().noneMatch(block -> block.mode() == CompressionMode.HUFFMAN_2_BYTE));
        assertTrue(wordOnlyReport.blocks().stream().noneMatch(block -> block.mode() == CompressionMode.HUFFMAN_1_BYTE));
    }

    @Test
    void codecChoosesBlockStrategyWhenBlocksHaveIndependentLocalPatterns() throws IOException {
        byte[] data = new byte[BLOCK_TEST_SIZE * 2];
        Arrays.fill(data, 0, BLOCK_TEST_SIZE, (byte) 'A');
        Arrays.fill(data, BLOCK_TEST_SIZE, data.length, (byte) 'B');
        Path inputPath = writeInput("block-strategy", data);
        Path archivePath = tempDir.resolve("block-strategy.hff");

        CompressionStats result = codec.compress(inputPath, archivePath);

        assertEquals(CompressionMode.HUFFMAN_BLOCK, result.mode());
    }

    @Test
    void roundTripRandomBinaryFiles() throws IOException {
        int[] sizes = {1, 2, 7, 8, 31, 255, 1024, 8192};
        Random random = new Random(20260416L);

        for (int size : sizes) {
            byte[] data = new byte[size];
            random.nextBytes(data);
            roundTrip("random-" + size, data);
        }
    }

    @Test
    void compressorFallsBackToStoredModeWhenHuffmanWouldBeWorse() throws IOException {
        byte[] data = {0, 1, 2, 3, 4, 5};
        RoundTrip roundTrip = roundTrip("stored", data);

        assertEquals(CompressionMode.STORED, roundTrip.compressionStats.mode());
        assertEquals(data.length + 13L, roundTrip.compressionStats.archiveSize());
    }

    @Test
    void compressionIsDeterministicForEqualFrequencyInputs() throws IOException {
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i % 4) + 10);
        }

        Path inputPath = writeInput("deterministic-input", data);
        Path archivePath1 = tempDir.resolve("deterministic-1.hff");
        Path archivePath2 = tempDir.resolve("deterministic-2.hff");

        codec.compress(inputPath, archivePath1);
        codec.compress(inputPath, archivePath2);

        assertArrayEquals(Files.readAllBytes(archivePath1), Files.readAllBytes(archivePath2));
    }

    @Test
    void progressListenerReceivesAllPhases() throws IOException {
        byte[] data = "progress-test ".repeat(512).getBytes(StandardCharsets.UTF_8);
        Path inputPath = writeInput("progress-input", data);
        Path archivePath = tempDir.resolve("progress.hff");
        Path restoredPath = tempDir.resolve("progress-restored.bin");

        List<ProgressSnapshot> compressionSnapshots = new ArrayList<>();
        List<ProgressSnapshot> decompressionSnapshots = new ArrayList<>();

        codec.compress(inputPath, archivePath, compressionSnapshots::add);
        codec.decompress(archivePath, restoredPath, decompressionSnapshots::add);

        assertFalse(compressionSnapshots.isEmpty());
        assertFalse(decompressionSnapshots.isEmpty());
        assertTrue(compressionSnapshots.stream().anyMatch(snapshot -> snapshot.phase() == ProgressPhase.ANALYZING));
        assertTrue(compressionSnapshots.stream().anyMatch(snapshot -> snapshot.phase() == ProgressPhase.COMPRESSING));
        assertTrue(decompressionSnapshots.stream().allMatch(snapshot -> snapshot.phase() == ProgressPhase.DECOMPRESSING));
        assertEquals(1.0, compressionSnapshots.get(compressionSnapshots.size() - 1).completion(), 1.0e-9);
        assertEquals(1.0, decompressionSnapshots.get(decompressionSnapshots.size() - 1).completion(), 1.0e-9);
        assertArrayEquals(data, Files.readAllBytes(restoredPath));
    }

    @Test
    void decompressRejectsInvalidMagic() throws IOException {
        Path archivePath = tempDir.resolve("invalid-magic.hff");
        Files.write(archivePath, new byte[] {'N', 'O', 'P', 'E'});

        assertThrows(ArchiveFormatException.class,
            () -> codec.decompress(archivePath, tempDir.resolve("invalid-magic.bin")));
    }

    @Test
    void decompressRejectsUnsupportedModeId() throws IOException {
        RoundTrip roundTrip = roundTrip("mode", "version test".getBytes(StandardCharsets.UTF_8));
        byte[] archive = Files.readAllBytes(roundTrip.archivePath);
        archive[4] = 99;

        Path mutatedArchive = tempDir.resolve("bad-mode.hff");
        Files.write(mutatedArchive, archive);

        assertThrows(ArchiveFormatException.class,
            () -> codec.decompress(mutatedArchive, tempDir.resolve("bad-mode.bin")));
    }

    @Test
    void decompressRejectsTruncatedStoredArchive() throws IOException {
        byte[] data = {1, 2, 3, 4, 5, 6};
        RoundTrip roundTrip = roundTrip("truncated-stored", data);

        byte[] archive = Files.readAllBytes(roundTrip.archivePath);
        Path truncatedArchive = tempDir.resolve("truncated-stored-copy.hff");
        Files.write(truncatedArchive, Arrays.copyOf(archive, archive.length - 1));

        assertThrows(ArchiveFormatException.class,
            () -> codec.decompress(truncatedArchive, tempDir.resolve("truncated-stored.bin")));
    }

    @Test
    void decompressRejectsTruncatedHuffmanArchive() throws IOException {
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 2 == 0 ? 'A' : 'B');
        }

        RoundTrip roundTrip = roundTrip("truncated-huffman", data);
        assertTrue(roundTrip.compressionStats.mode() != CompressionMode.STORED);

        byte[] archive = Files.readAllBytes(roundTrip.archivePath);
        Path truncatedArchive = tempDir.resolve("truncated-huffman-copy.hff");
        Files.write(truncatedArchive, Arrays.copyOf(archive, archive.length - 1));

        assertThrows(ArchiveFormatException.class,
            () -> codec.decompress(truncatedArchive, tempDir.resolve("truncated-huffman.bin")));
    }

    @Test
    void decompressRejectsFrequencyTableWithWrongTotal() throws IOException {
        Path archivePath = tempDir.resolve("bad-total.hff");
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(archivePath))) {
            output.write(new byte[] {'H', 'U', 'F', 'F'});
            output.writeByte(CompressionMode.HUFFMAN_1_BYTE.id());
            output.writeLong(10L);
            output.writeInt(1);
            output.writeByte(65);
            output.writeLong(9L);
        }

        assertThrows(ArchiveFormatException.class,
            () -> codec.decompress(archivePath, tempDir.resolve("bad-total.bin")));
    }

    private RoundTrip roundTrip(String baseName, byte[] data) throws IOException {
        Path inputPath = writeInput(baseName + "-input", data);
        Path archivePath = tempDir.resolve(baseName + ".hff");
        Path restoredPath = tempDir.resolve(baseName + "-restored.bin");

        CompressionStats compressionStats = codec.compress(inputPath, archivePath);
        DecompressionStats decompressionStats = codec.decompress(archivePath, restoredPath);

        assertEquals(compressionStats.mode(), decompressionStats.mode());
        assertEquals(data.length, decompressionStats.restoredSize());
        assertArrayEquals(data, Files.readAllBytes(restoredPath));

        return new RoundTrip(inputPath, archivePath, restoredPath, compressionStats, decompressionStats);
    }

    private Path writeInput(String fileName, byte[] data) throws IOException {
        Path inputPath = tempDir.resolve(fileName + ".bin");
        Files.write(inputPath, data);
        return inputPath;
    }

    private Path createBenchmarkCorpus() throws IOException {
        Path corpusDirectory = tempDir.resolve("corpus");
        Files.createDirectories(corpusDirectory);
        Files.writeString(corpusDirectory.resolve("text.txt"), "benchmark text ".repeat(40));
        Files.write(corpusDirectory.resolve("binary.bin"), new byte[] {
            1, 1, 1, 1, 2, 2, 3, 5, 8, 13, 21, 34, 55, 89
        });
        return corpusDirectory;
    }

    private record RoundTrip(
        Path inputPath,
        Path archivePath,
        Path restoredPath,
        CompressionStats compressionStats,
        DecompressionStats decompressionStats
    ) {
    }
}
