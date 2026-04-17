package com.serafinebot.p4;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void cliCompressesAndDecompressesFiles() throws IOException {
        byte[] data = "CLI round-trip data ".repeat(200).getBytes(StandardCharsets.UTF_8);
        Path inputPath = tempDir.resolve("cli-input.txt");
        Path archivePath = tempDir.resolve("cli-output.hff");
        Path restoredPath = tempDir.resolve("cli-restored.txt");
        Files.write(inputPath, data);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int compressExit = Main.run(
            new String[] {"compress", inputPath.toString(), archivePath.toString()},
            new PrintStream(stdout),
            new PrintStream(stderr)
        );

        int decompressExit = Main.run(
            new String[] {"decompress", archivePath.toString(), restoredPath.toString()},
            new PrintStream(stdout),
            new PrintStream(stderr)
        );

        assertEquals(0, compressExit);
        assertEquals(0, decompressExit);
        assertArrayEquals(data, Files.readAllBytes(restoredPath));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Mode:"));
    }

    @Test
    void cliShowsHelpForInvalidArguments() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = Main.run(new String[] {"compress"}, new PrintStream(stdout), new PrintStream(stderr));

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }
}
