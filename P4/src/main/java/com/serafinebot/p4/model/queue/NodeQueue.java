package com.serafinebot.p4.model.queue;

/**
 * Minimal abstraction over the priority queue used by Huffman.
 *
 * <p>Huffman tree construction only needs insert, remove-min, and size. Keeping the interface this
 * small makes the binary heap, sorted list, and Fibonacci heap interchangeable for benchmarks
 * without forcing every implementation to expose operations such as decrease-key that this
 * assignment never uses.</p>
 *
 * @param <T> element type stored in the queue
 */
public interface NodeQueue<T> {
    /**
     * Inserts one element into the queue.
     */
    void add(T node);

    /**
     * Removes and returns the smallest element according to the queue ordering.
     */
    T removeMin();

    /**
     * Returns the current number of queued elements.
     */
    int size();
}
