package com.serafinebot.p4.model.archive;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Binary header stored before the archive payload.
 *
 * <p>The header contains enough information to reconstruct the Huffman tree later without keeping
 * the application running between compression and decompression. For Huffman archives it stores the
 * original file size plus a sparse frequency table. For stored or empty archives it stores only the
 * mode flags and the original size.</p>
 */
public final class ArchiveHeader {

    public static final int BASE_SIZE = 15;
    public static final int FREQUENCY_ENTRY_SIZE = 9;

    private static final byte[] MAGIC = {'H', 'U', 'F', 'F'};
    private static final int VERSION = 1;
    private static final int FLAG_HUFFMAN = 1;
    private static final int FLAG_EMPTY_PAYLOAD = 1 << 1;
    private static final int KNOWN_FLAGS_MASK = FLAG_HUFFMAN | FLAG_EMPTY_PAYLOAD;

    private final CompressionMode mode;
    private final long originalSize;
    private final long[] frequencies;
    private final boolean emptyPayload;

    private ArchiveHeader(CompressionMode mode, long originalSize, long[] frequencies, boolean emptyPayload) {
        this.mode = mode;
        this.originalSize = originalSize;
        this.frequencies = Arrays.copyOf(frequencies, frequencies.length);
        this.emptyPayload = emptyPayload;
    }

    /**
     * Creates the header used for an empty archive.
     */
    public static ArchiveHeader empty() {
        return new ArchiveHeader(CompressionMode.STORED, 0L, new long[256], true);
    }

    /**
     * Creates a stored-mode header.
     */
    public static ArchiveHeader stored(long originalSize) {
        if (originalSize == 0L) {
            return empty();
        }
        return new ArchiveHeader(CompressionMode.STORED, originalSize, new long[256], false);
    }

    /**
     * Creates a Huffman-mode header with a sparse frequency table.
     */
    public static ArchiveHeader huffman(long originalSize, long[] frequencies) {
        if (originalSize <= 0L) {
            throw new IllegalArgumentException("Els arxius Huffman requereixen una mida original positiva.");
        }
        return new ArchiveHeader(CompressionMode.HUFFMAN, originalSize, frequencies, false);
    }

    /**
     * Returns the logical archive mode.
     */
    public CompressionMode mode() {
        return mode;
    }

    /**
     * Returns the size of the original, uncompressed file in bytes.
     */
    public long originalSize() {
        return originalSize;
    }

    /**
     * Returns a defensive copy of the stored frequency table.
     */
    public long[] frequencies() {
        return Arrays.copyOf(frequencies, frequencies.length);
    }

    /**
     * Returns whether the archive represents an empty file.
     */
    public boolean emptyPayload() {
        return emptyPayload;
    }

    /**
     * Counts how many different byte values are present in the sparse table.
     */
    public int distinctSymbolCount() {
        int count = 0;
        for (long frequency : frequencies) if (frequency > 0L) count++;
        return count;
    }

    /**
     * Returns the total size of the serialized header in bytes.
     */
    public long sizeInBytes() {
        if (emptyPayload) return BASE_SIZE;
        if (mode == CompressionMode.HUFFMAN) return BASE_SIZE + (long) distinctSymbolCount() * FREQUENCY_ENTRY_SIZE;
        return BASE_SIZE;
    }

    /**
     * Serializes this header to the output stream.
     */
    public void write(DataOutputStream output) throws IOException {
        output.write(MAGIC);
        output.writeByte(VERSION);
        output.writeByte(flags());
        output.writeLong(originalSize);
        output.writeByte(encodedSymbolCount());

        if (emptyPayload || mode != CompressionMode.HUFFMAN) return;

        // Huffman mode stores only the symbols that actually appear in the file. The tree is later
        // reconstructed from these entries plus the deterministic tie-breaking rule.
        for (int symbol = 0; symbol < frequencies.length; symbol++) {
            long frequency = frequencies[symbol];
            if (frequency == 0L) continue;
            output.writeByte(symbol);
            output.writeLong(frequency);
        }
    }

    /**
     * Parses a header from the input stream and validates all invariants needed by the decoder.
     */
    public static ArchiveHeader read(DataInputStream input) throws IOException {
        byte[] magic = new byte[MAGIC.length];
        input.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new ArchiveFormatException("Capcalera magica de l'arxiu no valida.");
        }

        int version = input.readUnsignedByte();
        if (version != VERSION) {
            throw new ArchiveFormatException("Versio d'arxiu no suportada: " + version);
        }

        int flags = input.readUnsignedByte();
        if ((flags & ~KNOWN_FLAGS_MASK) != 0) {
            throw new ArchiveFormatException("Flags d'arxiu no suportats: " + flags);
        }

        long originalSize = input.readLong();
        if (originalSize < 0L) {
            throw new ArchiveFormatException("La mida original de la capcalera no pot ser negativa.");
        }

        int encodedSymbolCount = input.readUnsignedByte();

        boolean huffman = (flags & FLAG_HUFFMAN) != 0;
        boolean emptyPayload = (flags & FLAG_EMPTY_PAYLOAD) != 0;

        // Empty, stored, and Huffman archives share the same fixed prefix. The flags determine how
        // the remaining bytes should be interpreted.
        if (emptyPayload) {
            if (huffman) {
                throw new ArchiveFormatException("Els arxius buits no poden activar el flag Huffman.");
            }
            if (originalSize != 0L) {
                throw new ArchiveFormatException("Els arxius buits han de declarar una mida original de 0.");
            }
            if (encodedSymbolCount != 0) {
                throw new ArchiveFormatException("Els arxius buits han d'emmagatzemar un comptador de simbols igual a zero.");
            }
            return empty();
        }

        if (originalSize == 0L) {
            throw new ArchiveFormatException("Els arxius de mida zero han d'activar el flag de carrega buida.");
        }

        if (!huffman) {
            if (encodedSymbolCount != 0) {
                throw new ArchiveFormatException("Els arxius emmagatzemats han de guardar un comptador de simbols igual a zero.");
            }
            return stored(originalSize);
        }

        int symbolCount = encodedSymbolCount + 1;

        long[] frequencies = new long[256];
        long total = 0L;
        for (int i = 0; i < symbolCount; i++) {
            int symbol = input.readUnsignedByte();
            long frequency = input.readLong();

            if (frequency <= 0L) {
                throw new ArchiveFormatException("Frequencia no valida i no positiva per al simbol " + symbol + ".");
            }
            if (frequencies[symbol] != 0L) {
                throw new ArchiveFormatException("Entrada de frequencia duplicada per al simbol " + symbol + ".");
            }

            frequencies[symbol] = frequency;
            total += frequency;
        }

        if (total != originalSize) {
            throw new ArchiveFormatException("La suma de la taula de frequencies no coincideix amb la mida original.");
        }

        return huffman(originalSize, frequencies);
    }

    /**
     * Encodes the logical mode and empty-state into the on-disk flag byte.
     */
    private int flags() {
        int flags = 0;
        if (mode == CompressionMode.HUFFMAN) {
            flags |= FLAG_HUFFMAN;
        }
        if (emptyPayload) {
            flags |= FLAG_EMPTY_PAYLOAD;
        }
        return flags;
    }

    /**
     * Encodes the number of distinct Huffman symbols as {@code count - 1} to fit 1..256 values in
     * a single byte. Stored and empty archives ignore this byte and write zero.
     */
    private int encodedSymbolCount() {
        if (emptyPayload || mode != CompressionMode.HUFFMAN) {
            return 0;
        }
        return distinctSymbolCount() - 1;
    }
}
