package com.serafinebot.p4;

import com.serafinebot.p4.model.benchmark.BenchmarkConfig;
import com.serafinebot.p4.model.benchmark.BenchmarkCsvWriter;
import com.serafinebot.p4.model.benchmark.BenchmarkProgressSnapshot;
import com.serafinebot.p4.model.benchmark.BenchmarkReport;
import com.serafinebot.p4.model.benchmark.BenchmarkService;
import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressSnapshot;
import com.serafinebot.p4.model.report.CompressionStats;
import com.serafinebot.p4.model.report.DecompressionStats;
import com.serafinebot.p4.util.ByteFormat;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Command-line entry point for compressing and decompressing files.
 */
public final class CLI {

    private CLI() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            printHelp(err);
            return 1;
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        if ("--help".equals(command) || "-h".equals(command) || "help".equals(command)) {
            printHelp(out);
            return 0;
        }

        return switch (command) {
            case "compress", "c" -> runCompress(args, out, err);
            case "decompress", "d" -> runDecompress(args, out, err);
            case "benchmark", "bench", "b" -> runBenchmark(args, out, err);
            default -> {
                err.println("Unknown command: " + args[0]);
                printHelp(err);
                yield 1;
            }
        };
    }

    private static int runCompress(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 3) {
            err.println("Compress command expects exactly 2 arguments: <input> <output>.");
            printHelp(err);
            return 1;
        }

        Path inputPath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        HuffmanCodec codec = new HuffmanCodec();
        CliProgressPrinter progressPrinter = new CliProgressPrinter(err);

        try {
            CompressionStats result = codec.compress(inputPath, outputPath, progressPrinter::print);
            progressPrinter.finish();
            printCompressionResult(out, inputPath, outputPath, result);
            return 0;
        } catch (Exception exception) {
            progressPrinter.finish();
            err.println("Error: " + exception.getMessage());
            return 1;
        }
    }

    private static int runDecompress(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 3) {
            err.println("Decompress command expects exactly 2 arguments: <input> <output>.");
            printHelp(err);
            return 1;
        }

        Path inputPath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        HuffmanCodec codec = new HuffmanCodec();
        CliProgressPrinter progressPrinter = new CliProgressPrinter(err);

        try {
            DecompressionStats result = codec.decompress(inputPath, outputPath, progressPrinter::print);
            progressPrinter.finish();
            printDecompressionResult(out, inputPath, outputPath, result);
            return 0;
        } catch (Exception exception) {
            progressPrinter.finish();
            err.println("Error: " + exception.getMessage());
            return 1;
        }
    }

    private static int runBenchmark(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 4) {
            err.println("Benchmark command expects exactly 3 arguments: <corpusDir> <repetitions> <outputCsv>.");
            printHelp(err);
            return 1;
        }

        CliBenchmarkProgressPrinter progressPrinter = new CliBenchmarkProgressPrinter(err);
        try {
            Path corpusDirectory = Path.of(args[1]);
            if (!Files.isDirectory(corpusDirectory)) {
                throw new IllegalArgumentException("Corpus directory does not exist or is not a directory: " + corpusDirectory);
            }

            int repetitions = parsePositiveInt(args[2], "repetitions");
            Path outputCsvPath = Path.of(args[3]);

            Path parent = outputCsvPath.toAbsolutePath().normalize().getParent();
            if (parent != null && !Files.exists(parent)) {
                throw new IllegalArgumentException("Output directory does not exist: " + parent);
            }

            BenchmarkConfig config = new BenchmarkConfig(corpusDirectory, repetitions);
            BenchmarkReport report = new BenchmarkService().run(config, progressPrinter::print);
            progressPrinter.finish();
            BenchmarkCsvWriter.write(report, outputCsvPath);
            printBenchmarkResult(out, config, report, outputCsvPath);
            return 0;
        } catch (Exception exception) {
            progressPrinter.finish();
            err.println("Error: " + exception.getMessage());
            return 1;
        }
    }

    private static void printCompressionResult(PrintStream out,
                                               Path inputPath,
                                               Path outputPath,
                                               CompressionStats result) {
        out.printf("Compressed %s -> %s%n", inputPath, outputPath);
        out.printf("Mode: %s%n", result.mode());
        out.printf("Queue: %s%n", result.priorityQueueStrategy());
        out.printf("Original size: %d bytes%n", result.originalSize());
        out.printf("Archive size: %d bytes%n", result.archiveSize());
        out.printf("Header size: %d bytes%n", result.overheadSize());
        out.printf("Compression: %.2f%%%n", result.compressionPercentage());
        out.printf("Entropy: %.6f bits/symbol%n", result.entropy());
        out.printf("Average Huffman length: %.6f bits/symbol%n", result.averageHuffmanCodeLength());
        out.printf("Elapsed: %d ms%n", result.elapsedMillis());
    }

    private static void printDecompressionResult(PrintStream out,
                                                  Path inputPath,
                                                  Path outputPath,
                                                  DecompressionStats result) {
        out.printf("Decompressed %s -> %s%n", inputPath, outputPath);
        out.printf("Mode: %s%n", result.mode());
        out.printf("Archive size: %d bytes%n", result.archiveSize());
        out.printf("Restored size: %d bytes%n", result.restoredSize());
        out.printf("Elapsed: %d ms%n", result.elapsedMillis());
    }

    private static void printBenchmarkResult(PrintStream out,
                                             BenchmarkConfig config,
                                             BenchmarkReport report,
                                             Path outputCsvPath) {
        out.println("Benchmark completed.");
        out.printf("Corpus: %s%n", config.corpusDirectory().toAbsolutePath().normalize());
        out.printf("Repetitions: %d%n", config.repetitions());
        out.printf("Measurements: %d%n", report.queuePoints().size());
        out.printf("CSV: %s%n", outputCsvPath.toAbsolutePath().normalize());
    }

    private static int parsePositiveInt(String rawValue, String label) {
        int parsedValue;
        try {
            parsedValue = Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + label + ": " + rawValue);
        }
        if (parsedValue <= 0) {
            throw new IllegalArgumentException("The " + label + " must be positive.");
        }
        return parsedValue;
    }

    private static void printHelp(PrintStream stream) {
        stream.println("Usage:");
        stream.println("  java -cp target/P4-1.0-SNAPSHOT.jar com.serafinebot.p4.CLI <compress|decompress> <input> <output>");
        stream.println("  java -cp target/P4-1.0-SNAPSHOT.jar com.serafinebot.p4.CLI benchmark <corpusDir> <repetitions> <outputCsv>");
        stream.println();
        stream.println("Commands:");
        stream.println("  compress, c    Compress a file into .hff format");
        stream.println("  decompress, d  Restore a file from .hff format");
        stream.println("  benchmark, b   Run the comparatives benchmark and export CSV");
        stream.println();
        stream.println("Examples:");
        stream.println("  java -cp target/P4-1.0-SNAPSHOT.jar com.serafinebot.p4.CLI compress input.txt input.hff");
        stream.println("  java -cp target/P4-1.0-SNAPSHOT.jar com.serafinebot.p4.CLI decompress input.hff restored.bin");
        stream.println("  java -cp target/P4-1.0-SNAPSHOT.jar com.serafinebot.p4.CLI benchmark /path/to/corpus 3 comparativa.csv");
    }

    private static final class CliBenchmarkProgressPrinter {

        private final PrintStream err;
        private int previousLength;
        private boolean activeLine;

        private CliBenchmarkProgressPrinter(PrintStream err) {
            this.err = err;
        }

        void print(BenchmarkProgressSnapshot snapshot) {
            String line = String.format(
                "Benchmark %6.2f%% (%d/%d) %s | %s | %s",
                snapshot.completion() * 100.0,
                snapshot.completedSteps(),
                snapshot.totalSteps(),
                snapshot.sourceName(),
                snapshot.strategy(),
                ByteFormat.format(snapshot.sizeBytes())
            );

            int padding = Math.max(0, previousLength - line.length());
            err.print('\r' + line + " ".repeat(padding));
            activeLine = true;
            previousLength = line.length();

            if (snapshot.completion() >= 1.0) {
                err.println();
                activeLine = false;
                previousLength = 0;
            }
        }

        void finish() {
            if (activeLine) {
                err.println();
                activeLine = false;
                previousLength = 0;
            }
        }
    }

    private static final class CliProgressPrinter {

        private final PrintStream err;
        private ProgressPhase currentPhase;
        private int previousLength;
        private boolean activeLine;

        private CliProgressPrinter(PrintStream err) {
            this.err = err;
        }

        void print(ProgressSnapshot snapshot) {
            if (currentPhase != null && currentPhase != snapshot.phase() && activeLine) {
                err.println();
                activeLine = false;
                previousLength = 0;
            }

            currentPhase = snapshot.phase();
            String line = format(snapshot);
            int padding = Math.max(0, previousLength - line.length());
            err.print('\r' + line + " ".repeat(padding));
            activeLine = true;
            previousLength = line.length();

            if (snapshot.completion() >= 1.0) {
                err.println();
                activeLine = false;
                previousLength = 0;
            }
        }

        void finish() {
            if (activeLine) {
                err.println();
                activeLine = false;
                previousLength = 0;
            }
        }

        private String format(ProgressSnapshot snapshot) {
            long totalBytes = snapshot.totalBytes();
            long processedBytes = snapshot.processedBytes();
            double percent = totalBytes == 0L ? 100.0 : (processedBytes * 100.0) / totalBytes;
            String eta = snapshot.estimatedRemainingMillis() < 0L
                ? "ETA --"
                : "ETA " + formatDuration(snapshot.estimatedRemainingMillis());

            return String.format("%s %6.2f%% (%s / %s) %s",
                formatPhase(snapshot.phase()),
                percent,
                ByteFormat.format(processedBytes),
                ByteFormat.format(totalBytes),
                eta);
        }

        private String formatPhase(ProgressPhase phase) {
            return switch (phase) {
                case ANALYZING -> "Analyzing   ";
                case COMPRESSING -> "Compressing ";
                case DECOMPRESSING -> "Decompressing";
            };
        }

        private String formatDuration(long millis) {
            long totalSeconds = millis / 1000L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
