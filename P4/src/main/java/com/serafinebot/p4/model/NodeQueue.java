package com.serafinebot.p4.model;

/**
 * Minimal abstraction over the priority queue used by Huffman.
 *
 * @param <T> element type stored in the queue
 */
interface NodeQueue<T> {
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
