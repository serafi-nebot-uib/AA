package com.serafinebot.p4.model.huffman;

import com.serafinebot.p4.model.codec.FrequencyTable;
import com.serafinebot.p4.model.codec.HuffmanCode;
import com.serafinebot.p4.model.queue.BinaryHeapNodeQueue;
import com.serafinebot.p4.model.queue.NodeQueue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuffmanCodeTest {

    @Test
    void leavesProvideDirectLookupForAllSymbolsIncluding255() {
        long[] frequencies = new long[256];
        frequencies[0] = 2L;
        frequencies[127] = 3L;
        frequencies[255] = 5L;

        NodeQueue<HuffmanCode> queue = new BinaryHeapNodeQueue<>();
        HuffmanCode root = HuffmanCode.buildTree(FrequencyTable.fromFrequencies(frequencies), queue);
        HuffmanCode[] leaves = root.leaves();

        assertNotNull(leaves[0]);
        assertNotNull(leaves[127]);
        assertNotNull(leaves[255]);
        assertEquals(255, leaves[255].symbol());
        assertSame(leaves[255], root.leaves()[255]);
    }

    @Test
    void codesAndDepthsAreInitializedLazilyAndConsistently() {
        long[] frequencies = new long[256];
        frequencies['A'] = 10L;
        frequencies['B'] = 3L;
        frequencies['C'] = 1L;

        NodeQueue<HuffmanCode> queue = new BinaryHeapNodeQueue<>();
        HuffmanCode root = HuffmanCode.buildTree(FrequencyTable.fromFrequencies(frequencies), queue);
        HuffmanCode[] leaves = root.leaves();

        assertArrayEquals(new byte[0], root.code());
        assertEquals(leaves['A'].depth(), leaves['A'].code().length);
        assertEquals(leaves['B'].depth(), leaves['B'].code().length);
        assertEquals(leaves['C'].depth(), leaves['C'].code().length);
        assertTrue(leaves['A'].depth() <= leaves['B'].depth());
        assertTrue(leaves['B'].depth() <= leaves['C'].depth());
    }
}
