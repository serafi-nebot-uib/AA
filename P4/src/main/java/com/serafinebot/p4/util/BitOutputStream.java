package com.serafinebot.p4.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes individual bits to an output stream.
 *
 * <p>Bits are packed most-significant-bit first. The last byte is padded with zeros on close if it
 * was not completely filled.</p>
 */
public final class BitOutputStream implements Closeable {

    private final OutputStream output;
    private int currentByte;
    private int bitCount;

    /**
     * Creates a bit writer over the given byte stream.
     */
    public BitOutputStream(OutputStream output) {
        this.output = output;
    }

    /**
     * Writes a complete Huffman code represented as a sequence of bits.
     */
    public void write(byte[] bits) throws IOException {
        for (byte bit : bits) writeBit(bit);
    }

    /**
     * Writes one bit into the current byte buffer and flushes when that byte is full.
     */
    private void writeBit(int bit) throws IOException {
        currentByte = (currentByte << 1) | (bit & 1);
        if (++bitCount == 8) flushCurrentByte();
    }

    /**
     * Flushes the current byte buffer. Partial bytes are right-padded with zeros.
     */
    private void flushCurrentByte() throws IOException {
        if (bitCount == 0) return;
        if (bitCount < 8) currentByte <<= (8 - bitCount);
        output.write(currentByte);
        currentByte = 0;
        bitCount = 0;
    }

    public void finish() throws IOException {
        flushCurrentByte();
    }

    @Override
    public void close() throws IOException {
        finish();
        output.close();
    }
}
