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
        builder.append("profile,strategy,size_bytes,compression_ms,decompression_ms,entropy,average_code_length,compression_percentage\n");
        for (BenchmarkPoint point : report.points()) {
            builder.append(point.profile().name()).append(',')
                .append(point.strategy().name()).append(',')
                .append(point.sizeBytes()).append(',')
                .append(format(point.compressionMillis())).append(',')
                .append(format(point.decompressionMillis())).append(',')
                .append(format(point.entropy())).append(',')
                .append(format(point.averageCodeLength())).append(',')
                .append(format(point.compressionPercentage())).append('\n');
        }
        return builder.toString();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }
}
