package com.serafinebot.p4.model;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * {@link NodeQueue} implementation backed by Java's binary heap priority queue.
 *
 * @param <T> element type stored in the queue
 */
final class BinaryHeapNodeQueue<T> implements NodeQueue<T> {

    private final PriorityQueue<T> queue;

    /**
     * Creates a queue that expects naturally comparable elements.
     */
    BinaryHeapNodeQueue() {
        this.queue = new PriorityQueue<>();
    }

    /**
     * Creates a queue that orders elements through the provided comparator.
     */
    BinaryHeapNodeQueue(Comparator<? super T> comparator) {
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
