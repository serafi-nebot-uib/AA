package com.serafinebot.p4;

import com.serafinebot.p4.model.codec.HuffmanCodec;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressSnapshot;
import com.serafinebot.p4.model.report.CompressionResult;
import com.serafinebot.p4.model.report.DecompressionResult;

import java.io.PrintStream;
import java.nio.file.Path;

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

        String command = args[0].toLowerCase();
        if ("--help".equals(command) || "-h".equals(command) || "help".equals(command)) {
            printHelp(out);
            return 0;
        }

        if (args.length != 3) {
            err.println("Expected exactly 3 arguments.");
            printHelp(err);
            return 1;
        }

        Path inputPath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        HuffmanCodec codec = new HuffmanCodec();
        CliProgressPrinter progressPrinter = new CliProgressPrinter(err);

        try {
            switch (command) {
                case "compress", "c" -> {
                    CompressionResult result = codec.compress(inputPath, outputPath, progressPrinter::print);
                    progressPrinter.finish();
                    printCompressionResult(out, inputPath, outputPath, result);
                    return 0;
                }
                case "decompress", "d" -> {
                    DecompressionResult result = codec.decompress(inputPath, outputPath, progressPrinter::print);
                    progressPrinter.finish();
                    printDecompressionResult(out, inputPath, outputPath, result);
                    return 0;
                }
                default -> {
                    err.println("Unknown command: " + args[0]);
                    printHelp(err);
                    return 1;
                }
            }
        } catch (Exception exception) {
            progressPrinter.finish();
            err.println("Error: " + exception.getMessage());
            return 1;
        }
    }

    private static void printCompressionResult(PrintStream out,
                                               Path inputPath,
                                               Path outputPath,
                                               CompressionResult result) {
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
                                                 DecompressionResult result) {
        out.printf("Decompressed %s -> %s%n", inputPath, outputPath);
        out.printf("Mode: %s%n", result.mode());
        out.printf("Archive size: %d bytes%n", result.archiveSize());
        out.printf("Restored size: %d bytes%n", result.restoredSize());
        out.printf("Elapsed: %d ms%n", result.elapsedMillis());
    }

    private static void printHelp(PrintStream stream) {
        stream.println("Usage: java -cp target/P4-1.0-SNAPSHOT.jar com.serafinebot.p4.CLI <compress|decompress> <input> <output>");
        stream.println();
        stream.println("Commands:");
        stream.println("  compress, c    Compress a file into .hff format");
        stream.println("  decompress, d  Restore a file from .hff format");
        stream.println();
        stream.println("Examples:");
        stream.println("  java -cp target/P4-1.0-SNAPSHOT.jar com.serafinebot.p4.CLI compress input.txt input.hff");
        stream.println("  java -cp target/P4-1.0-SNAPSHOT.jar com.serafinebot.p4.CLI decompress input.hff restored.bin");
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
                formatBytes(processedBytes),
                formatBytes(totalBytes),
                eta);
        }

        private String formatPhase(ProgressPhase phase) {
            return switch (phase) {
                case ANALYZING -> "Analyzing   ";
                case COMPRESSING -> "Compressing ";
                case DECOMPRESSING -> "Decompressing";
            };
        }

        private String formatBytes(long bytes) {
            String[] units = {"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
            double value = bytes;
            int unitIndex = 0;

            while (value >= 1024.0 && unitIndex < units.length - 1) {
                value /= 1024.0;
                unitIndex++;
            }

            if (unitIndex == 0) {
                return bytes + " " + units[unitIndex];
            }
            return String.format("%.2f %s", value, units[unitIndex]);
        }

        private String formatDuration(long millis) {
            long totalSeconds = millis / 1000L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}
