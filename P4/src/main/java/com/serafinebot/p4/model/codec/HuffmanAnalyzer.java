package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

/**
 * Shared base for analyzer classes that build Huffman trees and report tree-build time.
 *
 * <p>Whole-file and block analysis use different algorithms, but both repeatedly turn a
 * {@link FrequencyTable} into a {@link HuffmanCode} with the selected queue strategy. Keeping only
 * that common concern here avoids making the two analyzers inherit unrelated control flow.</p>
 */
abstract class HuffmanAnalyzer {

    private final PriorityQueueStrategy priorityQueueStrategy;
    private long treeBuildNanos;

    HuffmanAnalyzer(PriorityQueueStrategy priorityQueueStrategy) {
        this.priorityQueueStrategy = priorityQueueStrategy;
    }

    long treeBuildNanos() {
        return treeBuildNanos;
    }

    protected HuffmanCode buildTree(FrequencyTable table) {
        long t0 = System.nanoTime();
        HuffmanCode root = HuffmanCode.buildFromFrequencies(
            table,
            priorityQueueStrategy.createQueue()
        );
        treeBuildNanos += System.nanoTime() - t0;
        return root;
    }
}
