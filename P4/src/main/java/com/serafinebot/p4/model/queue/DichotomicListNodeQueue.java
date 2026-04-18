package com.serafinebot.p4.model.queue;

import java.util.ArrayList;
import java.util.Collections;

/**
 * {@link NodeQueue} implementation backed by a sorted array list using binary-search insertion.
 *
 * @param <T> element type stored in the queue
 */
public final class DichotomicListNodeQueue<T extends Comparable<? super T>> implements NodeQueue<T> {

    private final ArrayList<T> elements = new ArrayList<>();

    @Override
    public void add(T node) {
        int insertionIndex = Collections.binarySearch(elements, node);
        if (insertionIndex < 0) {
            insertionIndex = -insertionIndex - 1;
        }
        elements.add(insertionIndex, node);
    }

    @Override
    public T removeMin() {
        return elements.remove(0);
    }

    @Override
    public int size() {
        return elements.size();
    }
}
