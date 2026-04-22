package com.serafinebot.p4.model.archive;

import java.util.List;
import java.util.Set;

/**
 * Compression mode requested by the user or stored inside an archive.
 *
 * <p>{@link #AUTO}, {@link #HUFFMAN_BLOCK_1_BYTE}, and {@link #HUFFMAN_BLOCK_2_BYTE} are request
 * profiles: they guide compression but are never written to the archive header. The physical
 * archive header only stores {@link #STORED}, {@link #HUFFMAN_1_BYTE}, {@link #HUFFMAN_2_BYTE}, or
 * {@link #HUFFMAN_BLOCK}, because those are the formats the decompressor can read later without
 * knowing the user's original selection.</p>
 */
public enum CompressionMode {
    /**
     * Analyze every available mode and write the smallest valid archive.
     */
    AUTO(-1, "Automatic"),
    /**
     * Payload is copied byte-for-byte after the header.
     */
    STORED(0, "Emmagatzemat"),
    /**
     * One global Huffman tree over byte symbols.
     */
    HUFFMAN_1_BYTE(1, "Huffman 1 byte"),
    /**
     * One global Huffman tree over 2-byte symbols.
     */
    HUFFMAN_2_BYTE(2, "Huffman 2 bytes"),
    /**
     * Independent byte-based Huffman compression per block.
     */
    HUFFMAN_BLOCK(3, "Huffman per blocs"),
    /**
     * Block mode restricted to one-byte Huffman sub-blocks.
     */
    HUFFMAN_BLOCK_1_BYTE(-1, "Huffman per blocs 1 byte"),
    /**
     * Block mode restricted to two-byte Huffman sub-blocks.
     */
    HUFFMAN_BLOCK_2_BYTE(-1, "Huffman per blocs 2 bytes");

    private final int id;
    private final String displayName;

    CompressionMode(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int id() {
        if (!isArchiveMode()) {
            throw new IllegalStateException("El mode " + this + " no es pot escriure a la capcalera.");
        }
        return id;
    }

    public boolean isArchiveMode() {
        return id >= 0;
    }

    public boolean isUserSelectable() {
        return this != STORED;
    }

    public CompressionMode archiveMode() {
        return switch (this) {
            case AUTO -> null;
            case HUFFMAN_BLOCK_1_BYTE, HUFFMAN_BLOCK_2_BYTE -> HUFFMAN_BLOCK;
            default -> this;
        };
    }

    public Set<CompressionMode> allowedBlockHuffmanModes() {
        return switch (this) {
            case HUFFMAN_BLOCK_1_BYTE -> Set.of(HUFFMAN_1_BYTE);
            case HUFFMAN_BLOCK_2_BYTE -> Set.of(HUFFMAN_2_BYTE);
            default -> Set.of(HUFFMAN_1_BYTE, HUFFMAN_2_BYTE);
        };
    }

    public boolean usesAutomaticSelection() {
        return this == AUTO;
    }

    public static CompressionMode[] requestModes() {
        return new CompressionMode[] {
            AUTO,
            STORED,
            HUFFMAN_1_BYTE,
            HUFFMAN_2_BYTE,
            HUFFMAN_BLOCK,
            HUFFMAN_BLOCK_1_BYTE,
            HUFFMAN_BLOCK_2_BYTE
        };
    }

    public static List<CompressionMode> benchmarkModes() {
        return List.of(HUFFMAN_1_BYTE, HUFFMAN_2_BYTE, HUFFMAN_BLOCK_1_BYTE, HUFFMAN_BLOCK_2_BYTE);
    }

    public boolean usesGlobalFrequencyTable() {
        return this == HUFFMAN_1_BYTE || this == HUFFMAN_2_BYTE;
    }

    public int symbolWidthBytes() {
        if (this == HUFFMAN_1_BYTE) {
            return 1;
        }
        if (this == HUFFMAN_2_BYTE) {
            return 2;
        }
        return 0;
    }

    public static CompressionMode fromId(int id) throws ArchiveFormatException {
        for (CompressionMode mode : values()) {
            if (mode.isArchiveMode() && mode.id == id) {
                return mode;
            }
        }
        throw new ArchiveFormatException("Mode de compressio no suportat: " + id);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
