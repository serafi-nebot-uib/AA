package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.progress.ProgressListener;
import com.serafinebot.p4.model.progress.ProgressPhase;
import com.serafinebot.p4.model.progress.ProgressTracker;
import com.serafinebot.p4.util.BitOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the archive header and encoded payload once the codec has selected a plan.
 *
 * <p>This class does not decide whether Huffman is worthwhile. It trusts the already-built plan and
 * focuses on one responsibility: serializing that plan in the exact format the reader expects.</p>
 */
final class HuffmanArchiveWriter {

    private static final int BUFFER_SIZE = 8192;

    private HuffmanArchiveWriter() {
    }

    static void writeStored(Path inputPath, Path outputPath, long originalSize, ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput)) {
            ArchiveHeader.stored(originalSize).write(output);
            writeStoredPayload(inputPath, output, originalSize, listener);
        }
    }

    static void writeByte(Path inputPath, Path outputPath, WholePlan plan, ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput)) {
            plan.header().write(output);
            if (plan.table().distinctSymbolCount() <= 1) {
                new ProgressTracker(listener, ProgressPhase.COMPRESSING, plan.header().originalSize()).complete(plan.header().originalSize());
                return;
            }
            writeByteHuffmanPayload(inputPath, output, plan.leaves(), plan.header().originalSize(), listener);
        }
    }

    static void writeWord(Path inputPath, Path outputPath, WholePlan plan, ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput)) {
            plan.header().write(output);
            ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, plan.header().originalSize());
            try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
                BitOutputStream bitOutput = new BitOutputStream(output);
                int pendingByte = -1;
                int trailingByte = -1;
                byte[] buffer = new byte[BUFFER_SIZE];
                long processedBytes = 0L;
                int read;

                while ((read = input.read(buffer)) >= 0) {
                    for (int i = 0; i < read; i++) {
                        int value = buffer[i] & 0xFF;
                        if (pendingByte < 0) {
                            pendingByte = value;
                        } else {
                            bitOutput.write(plan.leaves()[(pendingByte << 8) | value].code());
                            pendingByte = -1;
                        }
                    }
                    processedBytes += read;
                    tracker.update(processedBytes);
                }

                if (pendingByte >= 0) {
                    trailingByte = pendingByte;
                }

                bitOutput.finish();
                if (trailingByte >= 0) {
                    output.writeByte(trailingByte);
                }
                tracker.complete(processedBytes);
            }
        }
    }

    static void writeBlock(Path inputPath, Path outputPath, BlockPlan blockPlan, ProgressListener listener) throws IOException {
        try (OutputStream rawOutput = new BufferedOutputStream(Files.newOutputStream(outputPath), BUFFER_SIZE);
             DataOutputStream output = new DataOutputStream(rawOutput);
             InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {

            blockPlan.header().write(output);
            ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, blockPlan.header().originalSize());
            byte[] buffer = new byte[blockPlan.blockSize()];
            long processedBytes = 0L;

            for (BlockUnit block : blockPlan.blocks()) {
                int read = readBlock(input, buffer, block.blockSize());
                if (read != block.blockSize()) {
                    throw new IOException("No s'ha pogut rellegir un bloc durant la compressio.");
                }

                output.writeInt(block.blockSize());
                output.writeByte(block.mode().id());

                switch (block.mode()) {
                    case STORED -> output.write(buffer, 0, read);
                    case HUFFMAN_1_BYTE -> writeByteBlock(output, buffer, read, block);
                    case HUFFMAN_2_BYTE -> writeWordBlock(output, buffer, read, block);
                    default -> throw new IOException("Mode de bloc no suportat: " + block.mode());
                }

                processedBytes += read;
                tracker.update(processedBytes);
            }

            tracker.complete(processedBytes);
        }
    }

    private static void writeByteBlock(DataOutputStream output, byte[] buffer, int read, BlockUnit block) throws IOException {
        output.writeInt(block.byteTable().distinctSymbolCount());
        writeByteFrequencyEntries(output, block.byteTable().copyFrequencies());
        if (block.byteTable().distinctSymbolCount() <= 1) {
            return;
        }

        BitOutputStream bitOutput = new BitOutputStream(output);
        for (int i = 0; i < read; i++) {
            bitOutput.write(block.byteLeaves()[buffer[i] & 0xFF].code());
        }
        bitOutput.finish();
    }

    private static void writeWordBlock(DataOutputStream output, byte[] buffer, int read, BlockUnit block) throws IOException {
        output.writeInt(block.wordTable().distinctSymbolCount());
        writeWordFrequencyEntries(output, block.wordTable().copyFrequencies());
        int pairEnd = read - (read & 1);
        if (block.wordTable().distinctSymbolCount() > 1) {
            BitOutputStream bitOutput = new BitOutputStream(output);
            for (int i = 0; i < pairEnd; i += 2) {
                int word = ((buffer[i] & 0xFF) << 8) | (buffer[i + 1] & 0xFF);
                bitOutput.write(block.wordLeaves()[word].code());
            }
            bitOutput.finish();
        }
        if ((read & 1) == 1) {
            output.writeByte(buffer[read - 1]);
        }
    }

    private static void writeByteFrequencyEntries(DataOutputStream output, long[] frequencies) throws IOException {
        for (int symbol = 0; symbol < frequencies.length; symbol++) {
            long frequency = frequencies[symbol];
            if (frequency == 0L) {
                continue;
            }
            output.writeByte(symbol);
            output.writeLong(frequency);
        }
    }

    private static void writeWordFrequencyEntries(DataOutputStream output, long[] frequencies) throws IOException {
        for (int symbol = 0; symbol < frequencies.length; symbol++) {
            long frequency = frequencies[symbol];
            if (frequency == 0L) {
                continue;
            }
            output.writeShort(symbol);
            output.writeLong(frequency);
        }
    }

    private static void writeByteHuffmanPayload(Path inputPath,
                                                OutputStream output,
                                                HuffmanCode[] leaves,
                                                long totalBytes,
                                                ProgressListener listener) throws IOException {
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, totalBytes);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            BitOutputStream bitOutput = new BitOutputStream(output);
            byte[] buffer = new byte[BUFFER_SIZE];
            long processedBytes = 0L;
            int read;

            while ((read = input.read(buffer)) >= 0) {
                for (int i = 0; i < read; i++) {
                    bitOutput.write(leaves[buffer[i] & 0xFF].code());
                }
                processedBytes += read;
                tracker.update(processedBytes);
            }

            bitOutput.finish();
            tracker.complete(processedBytes);
        }
    }

    private static void writeStoredPayload(Path inputPath,
                                           OutputStream output,
                                           long totalBytes,
                                           ProgressListener listener) throws IOException {
        ProgressTracker tracker = new ProgressTracker(listener, ProgressPhase.COMPRESSING, totalBytes);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(inputPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long processedBytes = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                processedBytes += read;
                tracker.update(processedBytes);
            }
            tracker.complete(processedBytes);
        }
    }

    private static int readBlock(InputStream input, byte[] buffer, int maxBytes) throws IOException {
        int totalRead = 0;
        while (totalRead < maxBytes) {
            int read = input.read(buffer, totalRead, maxBytes - totalRead);
            if (read < 0) {
                break;
            }
            totalRead += read;
        }
        return totalRead;
    }
}
