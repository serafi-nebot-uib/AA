package com.serafinebot.p4.model.archive;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 * Binary header stored before the archive payload.
 *
 * <p>The header stores the selected compression strategy and enough metadata to reconstruct the
 * decoding structures later. Strategies with one global Huffman tree carry a sparse frequency
 * table. Stored and block-based strategies use only the fixed header.</p>
 */
public final class ArchiveHeader {

    public static final int BASE_SIZE = 13;
    public static final int BYTE_FREQUENCY_ENTRY_SIZE = 9;
    public static final int WORD_FREQUENCY_ENTRY_SIZE = 10;

    private static final byte[] MAGIC = {'H', 'U', 'F', 'F'};

    private final CompressionMode mode;
    private final long originalSize;
    private final long[] frequencies;

    private ArchiveHeader(CompressionMode mode, long originalSize, long[] frequencies) {
        this.mode = mode;
        this.originalSize = originalSize;
        this.frequencies = Arrays.copyOf(frequencies, frequencies.length);
    }

    public static ArchiveHeader stored(long originalSize) {
        return new ArchiveHeader(CompressionMode.STORED, originalSize, new long[0]);
    }

    public static ArchiveHeader huffman1Byte(long originalSize, long[] frequencies) {
        return new ArchiveHeader(CompressionMode.HUFFMAN_1_BYTE, originalSize, frequencies);
    }

    public static ArchiveHeader huffman2Byte(long originalSize, long[] frequencies) {
        return new ArchiveHeader(CompressionMode.HUFFMAN_2_BYTE, originalSize, frequencies);
    }

    public static ArchiveHeader block(long originalSize) {
        return new ArchiveHeader(CompressionMode.HUFFMAN_BLOCK, originalSize, new long[0]);
    }

    public CompressionMode mode() {
        return mode;
    }

    public long originalSize() {
        return originalSize;
    }

    public long[] frequencies() {
        return Arrays.copyOf(frequencies, frequencies.length);
    }

    public int distinctSymbolCount() {
        int count = 0;
        for (long frequency : frequencies) {
            if (frequency > 0L) {
                count++;
            }
        }
        return count;
    }

    public long sizeInBytes() {
        if (!mode.usesGlobalFrequencyTable()) {
            return BASE_SIZE;
        }
        long entrySize = mode == CompressionMode.HUFFMAN_1_BYTE ? BYTE_FREQUENCY_ENTRY_SIZE : WORD_FREQUENCY_ENTRY_SIZE;
        return BASE_SIZE + Integer.BYTES + distinctSymbolCount() * entrySize;
    }

    public void write(DataOutputStream output) throws IOException {
        output.write(MAGIC);
        output.writeByte(mode.id());
        output.writeLong(originalSize);

        if (!mode.usesGlobalFrequencyTable()) {
            return;
        }

        output.writeInt(distinctSymbolCount());
        for (int symbol = 0; symbol < frequencies.length; symbol++) {
            long frequency = frequencies[symbol];
            if (frequency == 0L) {
                continue;
            }
            if (mode == CompressionMode.HUFFMAN_1_BYTE) {
                output.writeByte(symbol);
            } else {
                output.writeShort(symbol);
            }
            output.writeLong(frequency);
        }
    }

    public static ArchiveHeader read(DataInputStream input) throws IOException {
        try {
            byte[] magic = new byte[MAGIC.length];
            input.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new ArchiveFormatException("Capcalera magica de l'arxiu no valida.");
            }

            CompressionMode mode = CompressionMode.fromId(input.readUnsignedByte());
            long originalSize = input.readLong();
            if (originalSize < 0L) {
                throw new ArchiveFormatException("La mida original de la capcalera no pot ser negativa.");
            }

            if (!mode.usesGlobalFrequencyTable()) {
                return new ArchiveHeader(mode, originalSize, new long[0]);
            }

            int symbolCount = input.readInt();
            if (symbolCount < 0) {
                throw new ArchiveFormatException("El comptador de simbols no pot ser negatiu.");
            }

            int symbolSpaceSize = mode == CompressionMode.HUFFMAN_1_BYTE ? 256 : 65536;
            long[] frequencies = new long[symbolSpaceSize];
            long total = 0L;

            for (int i = 0; i < symbolCount; i++) {
                int symbol = mode == CompressionMode.HUFFMAN_1_BYTE ? input.readUnsignedByte() : input.readUnsignedShort();
                long frequency = input.readLong();

                if (frequency <= 0L) {
                    throw new ArchiveFormatException("Frequencia no valida i no positiva per al simbol " + symbol + '.');
                }
                if (frequencies[symbol] != 0L) {
                    throw new ArchiveFormatException("Entrada de frequencia duplicada per al simbol " + symbol + '.');
                }

                frequencies[symbol] = frequency;
                total += frequency;
            }

            if (mode == CompressionMode.HUFFMAN_1_BYTE && total != originalSize) {
                throw new ArchiveFormatException("La suma de la taula de frequencies no coincideix amb la mida original.");
            }
            if (mode == CompressionMode.HUFFMAN_2_BYTE && total != originalSize / 2) {
                throw new ArchiveFormatException("La suma de la taula de frequencies de 2 bytes no coincideix amb el nombre de parelles originals.");
            }

            return new ArchiveHeader(mode, originalSize, frequencies);
        } catch (EOFException exception) {
            throw new ArchiveFormatException("Final inesperat mentre es llegia la capcalera de l'arxiu.");
        }
    }
}
