package com.serafinebot.p4.model.queue;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * {@link NodeQueue} implementation backed by a sorted linked list.
 *
 * @param <T> element type stored in the queue
 */
public final class OrderedListNodeQueue<T extends Comparable<? super T>> implements NodeQueue<T> {

    private final LinkedList<T> elements = new LinkedList<>();

    @Override
    public void add(T node) {
        ListIterator<T> iterator = elements.listIterator();
        while (iterator.hasNext()) {
            if (node.compareTo(iterator.next()) <= 0) {
                iterator.previous();
                iterator.add(node);
                return;
            }
        }
        elements.addLast(node);
    }

    @Override
    public T removeMin() {
        return elements.removeFirst();
    }

    @Override
    public int size() {
        return elements.size();
    }
}
