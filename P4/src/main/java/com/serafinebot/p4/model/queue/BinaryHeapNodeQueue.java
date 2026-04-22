package com.serafinebot.p4.model.queue;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * {@link NodeQueue} implementation backed by Java's binary heap priority queue.
 *
 * <p>This is the production-default strategy: it is compact, well-tested by the JDK, and gives the
 * expected O(log n) insert/remove-min behavior for Huffman construction.</p>
 *
 * @param <T> element type stored in the queue
 */
public final class BinaryHeapNodeQueue<T> implements NodeQueue<T> {

    private final PriorityQueue<T> queue;

    /**
     * Creates a queue that expects naturally comparable elements.
     */
    public BinaryHeapNodeQueue() {
        this.queue = new PriorityQueue<>();
    }

    /**
     * Creates a queue that orders elements through the provided comparator.
     */
    public BinaryHeapNodeQueue(Comparator<? super T> comparator) {
        this.queue = new PriorityQueue<>(comparator);
    }

    @Override
    public void add(T node) {
        queue.add(node);
    }

    @Override
    public T removeMin() {
        return queue.remove();
    }

    @Override
    public int size() {
        return queue.size();
    }
}
