package com.serafinebot.p4.model.queue;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link NodeQueue} implementation backed by a Fibonacci heap.
 *
 * <p>This implementation only supports the operations required by Huffman construction. A complete
 * Fibonacci heap usually also exposes decrease-key and delete, but those would add complexity
 * without helping this assignment because Huffman nodes are inserted once and then removed by
 * priority.</p>
 *
 * <p>Each root list and child list is represented as a circular doubly linked list. That keeps
 * insertion and splicing cheap, but it also means every detach/link operation must leave the moved
 * node as a valid one-node circular list before it is inserted somewhere else.</p>
 *
 * @param <T> element type stored in the queue
 */
public final class FibonacciHeapNodeQueue<T extends Comparable<? super T>> implements NodeQueue<T> {

    private FibonacciNode<T> min;
    private int size;

    @Override
    public void add(T node) {
        FibonacciNode<T> fibonacciNode = new FibonacciNode<>(node);
        if (min == null) {
            min = fibonacciNode;
        } else {
            insertIntoRootList(fibonacciNode);
            if (fibonacciNode.value.compareTo(min.value) < 0) {
                min = fibonacciNode;
            }
        }
        size++;
    }

    @Override
    public T removeMin() {
        FibonacciNode<T> currentMin = min;
        if (currentMin == null) {
            throw new IllegalStateException("La cua esta buida.");
        }

        if (currentMin.child != null) {
            List<FibonacciNode<T>> children = collectCircularList(currentMin.child);
            for (FibonacciNode<T> child : children) {
                detach(child);
                child.parent = null;
                insertIntoRootList(child);
            }
            currentMin.child = null;
            currentMin.degree = 0;
        }

        if (currentMin.right == currentMin) {
            min = null;
        } else {
            FibonacciNode<T> nextRoot = currentMin.right;
            detach(currentMin);
            min = nextRoot;
            consolidate();
        }

        size--;
        return currentMin.value;
    }

    @Override
    public int size() {
        return size;
    }

    private void consolidate() {
        List<FibonacciNode<T>> roots = collectCircularList(min);
        ArrayList<FibonacciNode<T>> degreeTable = new ArrayList<>();

        // After removing the minimum, there may be many roots with the same degree. Consolidation
        // repeatedly links equal-degree roots so at most one root of each degree remains; this is
        // what keeps future remove-min operations logarithmic in the number of stored nodes.
        for (FibonacciNode<T> root : roots) {
            FibonacciNode<T> x = root;
            int degree = x.degree;
            ensureCapacity(degreeTable, degree);

            while (degreeTable.get(degree) != null) {
                FibonacciNode<T> y = degreeTable.get(degree);
                if (x.value.compareTo(y.value) > 0) {
                    FibonacciNode<T> temp = x;
                    x = y;
                    y = temp;
                }
                link(y, x);
                degreeTable.set(degree, null);
                degree++;
                ensureCapacity(degreeTable, degree);
            }

            degreeTable.set(degree, x);
        }

        min = null;
        for (FibonacciNode<T> node : degreeTable) {
            if (node == null) {
                continue;
            }
            node.left = node;
            node.right = node;
            if (min == null) {
                min = node;
            } else {
                insertIntoRootList(node);
                if (node.value.compareTo(min.value) < 0) {
                    min = node;
                }
            }
        }
    }

    private void link(FibonacciNode<T> child, FibonacciNode<T> parent) {
        // The higher-priority root stays a root; the other root becomes one of its children. The
        // child is detached first so its old siblings are not accidentally carried into the new
        // child list.
        detach(child);
        child.parent = parent;
        child.left = child;
        child.right = child;

        if (parent.child == null) {
            parent.child = child;
        } else {
            insertIntoCircularList(parent.child, child);
        }
        parent.degree++;
    }

    private void insertIntoRootList(FibonacciNode<T> node) {
        insertIntoCircularList(min, node);
    }

    private void insertIntoCircularList(FibonacciNode<T> anchor, FibonacciNode<T> node) {
        node.left = anchor;
        node.right = anchor.right;
        anchor.right.left = node;
        anchor.right = node;
    }

    private void detach(FibonacciNode<T> node) {
        // Detaching rewires the old neighbors and then resets the node to a self-loop. The self-loop
        // invariant makes it safe to insert the node into any other circular list immediately after.
        node.left.right = node.right;
        node.right.left = node.left;
        node.left = node;
        node.right = node;
    }

    private List<FibonacciNode<T>> collectCircularList(FibonacciNode<T> start) {
        List<FibonacciNode<T>> nodes = new ArrayList<>();
        FibonacciNode<T> current = start;
        // Snapshot the ring before mutating it. Consolidation and child promotion both change links,
        // so iterating directly over the circular list while mutating would be error-prone.
        do {
            nodes.add(current);
            current = current.right;
        } while (current != start);
        return nodes;
    }

    private void ensureCapacity(ArrayList<FibonacciNode<T>> table, int index) {
        while (table.size() <= index) {
            table.add(null);
        }
    }

    private static final class FibonacciNode<T> {
        private final T value;
        private FibonacciNode<T> parent;
        private FibonacciNode<T> child;
        private FibonacciNode<T> left;
        private FibonacciNode<T> right;
        private int degree;

        private FibonacciNode(T value) {
            this.value = value;
            this.left = this;
            this.right = this;
        }
    }
}
