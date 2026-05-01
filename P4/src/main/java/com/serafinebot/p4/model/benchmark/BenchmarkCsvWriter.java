package com.serafinebot.p4.model.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes benchmark reports to CSV.
 */
public final class BenchmarkCsvWriter {

    private BenchmarkCsvWriter() {
    }

    public static void write(BenchmarkReport report, Path outputPath) throws IOException {
        Files.writeString(outputPath, toCsv(report));
    }

    public static String toCsv(BenchmarkReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("source_name,strategy,size_bytes,tree_build_compression_ms,tree_build_decompression_ms\n");
        for (QueueBenchmarkPoint point : report.queuePoints()) {
            builder.append(point.sourceName()).append(',')
                .append(point.strategy().name()).append(',')
                .append(point.sizeBytes()).append(',')
                .append(format(point.compressionTreeBuildMillis())).append(',')
                .append(format(point.decompressionTreeBuildMillis())).append('\n');
        }

        if (!report.modePoints().isEmpty()) {
            builder.append('\n');
            builder.append("source_name,mode,size_bytes,compression_percentage,compression_ms,decompression_ms\n");
            for (CompressionModeBenchmarkPoint point : report.modePoints()) {
                builder.append(point.sourceName()).append(',')
                    .append(point.mode().name()).append(',')
                    .append(point.sizeBytes()).append(',')
                    .append(format(point.compressionPercentage())).append(',')
                    .append(format(point.compressionMillis())).append(',')
                    .append(format(point.decompressionMillis())).append('\n');
            }
        }

        return builder.toString();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }
}
