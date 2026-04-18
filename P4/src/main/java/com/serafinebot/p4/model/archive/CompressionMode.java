package com.serafinebot.p4.model.archive;

/**
 * Logical compression mode used by the codec.
 */
public enum CompressionMode {
    /**
     * Payload is copied byte-for-byte after the header.
     */
    STORED(0),
    /**
     * Payload is encoded with the Huffman tree described by the header metadata.
     */
    HUFFMAN(1);

    private final int flag;

    CompressionMode(int flag) {
        this.flag = flag;
    }

    /**
     * Returns the on-disk flag bit associated with this mode.
     */
    public int flag() {
        return flag;
    }
}
