package com.serafinebot.p4.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads individual bits from an input stream.
 *
 * <p>Bits are exposed most-significant-bit first. Each call consumes exactly one bit until the
 * underlying stream reaches EOF. This matches {@link BitOutputStream}'s packing order, so decoder
 * tree walks see the exact bit sequence that was emitted by the encoder.</p>
 */
public final class BitInputStream {

    private final InputStream input;
    private int currentByte;
    private int remainingBits;

    /**
     * Creates a bit reader over the given byte stream.
     */
    public BitInputStream(InputStream input) {
        this.input = input;
    }

    /**
     * Reads the next bit.
     *
     * @return {@code 0} or {@code 1}, or {@code -1} if the underlying stream reached EOF
     */
    public int readBit() throws IOException {
        if (remainingBits == 0) {
            currentByte = input.read();
            if (currentByte < 0) return -1;
            remainingBits = 8;
        }

        remainingBits--;
        return (currentByte >>> remainingBits) & 1;
    }
}
