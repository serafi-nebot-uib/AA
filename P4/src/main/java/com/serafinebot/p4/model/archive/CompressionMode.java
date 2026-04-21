package com.serafinebot.p4.model.archive;

/**
 * Logical compression strategy stored inside the archive.
 */
public enum CompressionMode {
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
    HUFFMAN_BLOCK(3, "Huffman per blocs");

    private final int id;
    private final String displayName;

    CompressionMode(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int id() {
        return id;
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
            if (mode.id == id) {
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
