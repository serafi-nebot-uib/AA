package com.serafinebot.p4.model.queue;

/**
 * Strategy used to build the Huffman tree.
 */
public enum PriorityQueueStrategy {
    /**
     * Java {@link java.util.PriorityQueue}-backed binary heap.
     */
    BINARY_HEAP("Heap binari"),

    /**
     * Array list kept sorted through binary-search insertion.
     */
    DICHOTOMIC_LIST("Llista dicotomica"),

    /**
     * Fibonacci heap implementation.
     */
    FIBONACCI_HEAP("Heap de Fibonacci");

    private final String displayName;

    PriorityQueueStrategy(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
