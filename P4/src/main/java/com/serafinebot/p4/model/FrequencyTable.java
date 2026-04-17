package com.serafinebot.p4.model;

import java.util.Arrays;

/**
 * Frequency table for byte-oriented Huffman coding.
 *
 * <p>The table always has 256 counters, one per possible byte value. Bytes are treated as unsigned
 * symbols in the range {@code 0..255}.</p>
 */
final class FrequencyTable {

    private final long[] frequencies = new long[256];
    private long totalCount;
    private int distinctSymbolCount;

    /**
     * Adds a block of bytes to the table.
     */
    void add(byte[] buffer, int length) {
        for (int i = 0; i < length; i++) addSymbol(buffer[i] & 0xFF);
    }

    /**
     * Increments the counter for one symbol.
     */
    void addSymbol(int symbol) {
        if (frequencies[symbol] == 0L) distinctSymbolCount++;
        frequencies[symbol]++;
        totalCount++;
    }

    /**
     * Returns the number of occurrences for one symbol.
     */
    long frequencyOf(int symbol) {
        return frequencies[symbol];
    }

    /**
     * Returns a defensive copy of the internal frequency array.
     */
    long[] copyFrequencies() {
        return Arrays.copyOf(frequencies, frequencies.length);
    }

    /**
     * Returns the total number of symbols seen.
     */
    long totalCount() {
        return totalCount;
    }

    /**
     * Returns the number of distinct byte values present in the table.
     */
    int distinctSymbolCount() {
        return distinctSymbolCount;
    }

    /**
     * Returns the only symbol present when the table contains exactly one distinct value, or
     * {@code -1} otherwise.
     */
    int singleSymbol() {
        if (distinctSymbolCount != 1) return -1;
        for (int symbol = 0; symbol < frequencies.length; symbol++)
            if (frequencies[symbol] > 0L) return symbol;
        return -1;
    }

    /**
     * Computes the Shannon entropy of the observed symbol distribution.
     */
    double entropy() {
        if (totalCount == 0L) return 0.0;
        double entropy = 0.0;
        for (long frequency : frequencies) {
            if (frequency == 0L) continue;
            double probability = frequency / (double) totalCount;
            entropy -= probability * (Math.log(probability) / Math.log(2.0));
        }
        return entropy;
    }

    /**
     * Builds a frequency table from an already-known array of counts.
     */
    static FrequencyTable fromFrequencies(long[] frequencies) {
        FrequencyTable table = new FrequencyTable();
        for (int symbol = 0; symbol < frequencies.length; symbol++) {
            long frequency = frequencies[symbol];
            if (frequency <= 0L) continue;
            table.frequencies[symbol] = frequency;
            table.totalCount += frequency;
            table.distinctSymbolCount++;
        }
        return table;
    }
}
