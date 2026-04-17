package com.serafinebot.p4.model;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Huffman tree node and code descriptor combined in a single type.
 *
 * <p>Leaves represent actual byte symbols. Internal nodes reuse {@code symbol} as the minimum
 * symbol contained in the subtree, which makes it a deterministic tie-breaker when two nodes have
 * the same frequency.</p>
 *
 * <p>The class also lazily derives two pieces of encoding metadata from the final tree:</p>
 *
 * <ul>
 *   <li>the bit code for each leaf, and</li>
 *   <li>a {@code HuffmanCode[256]} lookup table for O(1) symbol-to-code access.</li>
 * </ul>
 */
final class HuffmanCode implements Comparable<HuffmanCode> {
    private final int symbol;
    private final long frequency;
    private final HuffmanCode min;
    private final HuffmanCode max;
    private int depth;
    private HuffmanCode parent;
    private byte order; // 0: self is min node of parent
                        // 1: self is max node of parent
    private byte[] code;
    private boolean metadataInitialized;
    private HuffmanCode[] leaves;

    /**
     * Creates a leaf for one byte value.
     */
    public HuffmanCode(int symbol, long frequency) {
        this.symbol = symbol;
        this.frequency = frequency;
        this.depth = 0;
        this.order = 0;
        this.parent = null;
        this.min = null;
        this.max = null;
        this.code = null;
        this.metadataInitialized = false;
        this.leaves = null;
    }

    /**
     * Creates an internal node joining two children.
     */
    public HuffmanCode(int symbol, long frequency, HuffmanCode min, HuffmanCode max) {
        if (min.symbol == max.symbol) throw new IllegalArgumentException("c1 and c2 cannot be the same symbol");

        this.symbol = symbol;
        this.frequency = frequency;
        this.depth = 0;
        this.order = 0;
        this.parent = null;
        this.code = null;
        this.metadataInitialized = false;
        this.leaves = null;

        this.min = min;
        this.min.parent = this;
        this.min.order = 0;

        this.max = max;
        this.max.parent = this;
        this.max.order = 1;
    }

    /**
     * Initializes cached code metadata once for the whole tree rooted at this node.
     *
     * <p>The method performs a single breadth-first traversal from the root. During that pass it
     * assigns each node its depth, builds each leaf code, and fills the root's leaf lookup table.</p>
     */
    private void initMetadata() {
        HuffmanCode root = this;
        while (root.parent != null) root = root.parent;

        if (root.metadataInitialized) return;

        ArrayDeque<HuffmanCode> queue = new ArrayDeque<>();
        root.depth = 0;
        root.code = new byte[0];
        root.leaves = new HuffmanCode[256];
        queue.add(root);

        // Cache everything in one pass so encoding can later jump directly from a symbol to its
        // leaf and code without walking the tree again.
        while (!queue.isEmpty()) {
            HuffmanCode curr = queue.remove();

            if (curr.isLeaf()) {
                root.leaves[curr.symbol] = curr;
                continue;
            }

            if (curr.min != null) {
                curr.min.depth = curr.depth + 1;
                curr.min.code = Arrays.copyOf(curr.code, curr.min.depth);
                curr.min.code[curr.depth] = 0;
                queue.add(curr.min);
            }
            if (curr.max != null) {
                curr.max.depth = curr.depth + 1;
                curr.max.code = Arrays.copyOf(curr.code, curr.max.depth);
                curr.max.code[curr.depth] = 1;
                queue.add(curr.max);
            }
        }

        root.metadataInitialized = true;
    }

    /**
     * Creates a leaf node for the given symbol and frequency.
     */
    public static HuffmanCode leaf(int symbol, long frequency) {
        return new HuffmanCode(symbol, frequency);
    }

    /**
     * Combines two nodes into one internal node, ordering children deterministically.
     */
    public static HuffmanCode internal(HuffmanCode c1, HuffmanCode c2) {
        if (c1 == null || c2 == null) throw new IllegalArgumentException("c1 and c2 cannot be null");
        HuffmanCode min = c1.compareTo(c2) <= 0 ? c1 : c2;
        HuffmanCode max = min == c1 ? c2 : c1;
        return new HuffmanCode(min.symbol, c1.frequency + c2.frequency, min, max);
    }

    /**
     * Builds a deterministic Huffman tree from a frequency table using the provided queue.
     *
     * @param table byte frequency table
     * @param queue already-initialized priority queue implementation to use for this build
     * @return the root of the constructed tree, or {@code null} for an empty table
     */
    public static HuffmanCode buildTree(FrequencyTable table, NodeQueue<HuffmanCode> queue) {
        for (int symbol = 0; symbol < 256; symbol++) {
            long frequency = table.frequencyOf(symbol);
            if (frequency > 0L) queue.add(leaf(symbol, frequency));
        }

        if (queue.size() == 0) return null;

        while (queue.size() > 1) {
            HuffmanCode min = queue.removeMin();
            HuffmanCode max = queue.removeMin();
            queue.add(internal(min, max));
        }

        return queue.removeMin();
    }

    /**
     * Returns a cached lookup table from byte value to leaf node.
     */
    public HuffmanCode[] leaves() {
        initMetadata();
        HuffmanCode root = this;
        while (root.parent != null) root = root.parent;
        return root.leaves;
    }

    /**
     * Returns the bit code for this node. The value is meaningful for leaves; internal nodes only
     * expose it because code metadata is computed tree-wide and cached on every node.
     */
    public byte[] code() {
        initMetadata();
        return code;
    }

    /**
     * Returns the byte value for a leaf, or the minimum symbol contained in the subtree for an
     * internal node.
     */
    public int symbol() {
        return symbol;
    }

    /**
     * Returns the combined frequency represented by this node.
     */
    public long frequency() {
        return frequency;
    }

    /**
     * Returns the left child, which is the child with lower ordering priority.
     */
    public HuffmanCode min() {
        return min;
    }

    /**
     * Returns the right child, which is the child with higher ordering priority.
     */
    public HuffmanCode max() {
        return max;
    }

    /**
     * Returns whether this node is a leaf.
     */
    public boolean isLeaf() {
        return min == null && max == null;
    }

    /**
     * Returns the cached depth of this node from the root.
     */
    public int depth() {
        return depth;
    }

    @Override
    public int compareTo(HuffmanCode o) {
        int frequencyOrder = Long.compare(this.frequency, o.frequency);
        if (frequencyOrder != 0) return frequencyOrder;
        return Integer.compare(this.symbol, o.symbol);
    }
}
