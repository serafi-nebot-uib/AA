package com.serafinebot.p4.model.queue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class NodeQueueTest {

    static Stream<Arguments> queueImplementations() {
        return Stream.of(
            arguments("binary heap", (Supplier<NodeQueue<Integer>>) BinaryHeapNodeQueue::new),
            arguments("dichotomic list", (Supplier<NodeQueue<Integer>>) DichotomicListNodeQueue::new),
            arguments("fibonacci heap", (Supplier<NodeQueue<Integer>>) FibonacciHeapNodeQueue::new)
        );
    }

    @ParameterizedTest(name = "{0} removes values in sorted order")
    @MethodSource("queueImplementations")
    void removesValuesInComparableOrder(String name, Supplier<NodeQueue<Integer>> queueFactory) {
        NodeQueue<Integer> queue = queueFactory.get();
        List<Integer> values = List.of(5, -1, 5, 0, 13, 8, -1, 2);

        for (int value : values) {
            queue.add(value);
        }

        assertEquals(values.size(), queue.size());
        assertEquals(List.of(-1, -1, 0, 2, 5, 5, 8, 13), drain(queue));
        assertEquals(0, queue.size());
    }

    @ParameterizedTest(name = "{0} matches java PriorityQueue under mixed operations")
    @MethodSource("queueImplementations")
    void matchesReferencePriorityQueueUnderMixedOperations(String name, Supplier<NodeQueue<Integer>> queueFactory) {
        NodeQueue<Integer> queue = queueFactory.get();
        PriorityQueue<Integer> reference = new PriorityQueue<>();
        Random random = new Random(20260422L);

        for (int step = 0; step < 2_000; step++) {
            int value = random.nextInt(10_000) - 5_000;
            queue.add(value);
            reference.add(value);

            if (step % 3 == 0) {
                assertEquals(reference.remove(), queue.removeMin());
            }
            assertEquals(reference.size(), queue.size());
        }

        while (!reference.isEmpty()) {
            assertEquals(reference.remove(), queue.removeMin());
        }
        assertEquals(0, queue.size());
    }

    @ParameterizedTest(name = "{0} rejects removal from an empty queue")
    @MethodSource("queueImplementations")
    void removeMinFailsForEmptyQueue(String name, Supplier<NodeQueue<Integer>> queueFactory) {
        NodeQueue<Integer> queue = queueFactory.get();

        assertThrows(RuntimeException.class, queue::removeMin);
    }

    private List<Integer> drain(NodeQueue<Integer> queue) {
        List<Integer> values = new ArrayList<>();
        while (queue.size() > 0) {
            values.add(queue.removeMin());
        }
        return values;
    }
}
