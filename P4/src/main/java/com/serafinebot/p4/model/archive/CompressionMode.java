package com.serafinebot.p4.model.archive;

import java.util.List;
import java.util.Set;

/**
 * Compression mode requested by the user or stored inside an archive.
 *
 * <p>{@link #AUTO}, {@link #HUFFMAN_BLOCK_1_BYTE}, and {@link #HUFFMAN_BLOCK_2_BYTE} are request
 * profiles: they guide compression but are never written to the archive header. {@link #STORED}
 * remains an archive/block mode so older stored archives and stored blocks can be read, but it is
 * not offered as a whole-file compression request.</p>
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

    public Set<CompressionMode> allowedBlockHuffmanModes() {
        return switch (this) {
            case HUFFMAN_BLOCK_1_BYTE -> Set.of(HUFFMAN_1_BYTE);
            case HUFFMAN_BLOCK_2_BYTE -> Set.of(HUFFMAN_2_BYTE);
            default -> Set.of(HUFFMAN_1_BYTE, HUFFMAN_2_BYTE);
        };
    }

    public static CompressionMode[] requestModes() {
        return new CompressionMode[] {
            AUTO,
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
