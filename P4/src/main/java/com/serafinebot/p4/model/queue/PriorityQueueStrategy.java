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

    /**
     * Creates the queue implementation represented by this strategy.
     *
     * <p>The Huffman algorithm only depends on {@link NodeQueue}; keeping this factory here makes
     * the enum the single place that maps the user-visible strategy to the concrete data structure.</p>
     */
    public <T extends Comparable<? super T>> NodeQueue<T> createQueue() {
        return switch (this) {
            case BINARY_HEAP -> new BinaryHeapNodeQueue<>();
            case DICHOTOMIC_LIST -> new DichotomicListNodeQueue<>();
            case FIBONACCI_HEAP -> new FibonacciHeapNodeQueue<>();
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
