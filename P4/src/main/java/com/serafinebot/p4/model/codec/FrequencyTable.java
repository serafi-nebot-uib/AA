package com.serafinebot.p4.model.codec;

import java.util.Arrays;

/**
 * Frequency table for Huffman coding over a fixed symbol space.
 *
 * <p>The same structure is used for byte symbols ({@code 0..255}) and two-byte symbols
 * ({@code 0..65535}). Keeping the symbol space fixed makes lookups O(1), which is more useful here
 * than a sparse map because the table is read repeatedly while estimating encoded sizes and writing
 * archive metadata.</p>
 */
public final class FrequencyTable {

    private final long[] frequencies;
    private long totalCount;
    private int distinctSymbolCount;

    public FrequencyTable() {
        this(256);
    }

    public FrequencyTable(int symbolSpaceSize) {
        this.frequencies = new long[symbolSpaceSize];
    }

    /**
     * Adds a block of bytes to the table.
     */
    public void add(byte[] buffer, int length) {
        for (int i = 0; i < length; i++) addSymbol(buffer[i] & 0xFF);
    }

    /**
     * Increments the counter for one symbol.
     *
     * <p>{@code distinctSymbolCount} is cached because header-size estimation needs it often. It is
     * updated only on the transition from zero to one occurrence.</p>
     */
    public void addSymbol(int symbol) {
        if (frequencies[symbol] == 0L) distinctSymbolCount++;
        frequencies[symbol]++;
        totalCount++;
    }

    /**
     * Returns the number of occurrences for one symbol.
     */
    public long frequencyOf(int symbol) {
        return frequencies[symbol];
    }

    /**
     * Returns a defensive copy of the internal frequency array.
     */
    public long[] copyFrequencies() {
        return Arrays.copyOf(frequencies, frequencies.length);
    }

    public int symbolSpaceSize() {
        return frequencies.length;
    }

    /**
     * Returns the total number of symbols seen.
     */
    public long totalCount() {
        return totalCount;
    }

    /**
     * Returns the number of distinct byte values present in the table.
     */
    public int distinctSymbolCount() {
        return distinctSymbolCount;
    }

    /**
     * Returns the only symbol present when the table contains exactly one distinct value, or
     * {@code -1} otherwise.
     */
    public int singleSymbol() {
        if (distinctSymbolCount != 1) return -1;
        for (int symbol = 0; symbol < frequencies.length; symbol++)
            if (frequencies[symbol] > 0L) return symbol;
        return -1;
    }

    /**
     * Computes the Shannon entropy of the observed symbol distribution.
     *
     * <p>The value is not used to build the Huffman tree directly. It is kept as a lower-bound
     * reference for the report so the implementation can compare observed entropy with the average
     * generated code length.</p>
     */
    public double entropy() {
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
     *
     * <p>Archive headers store sparse frequency entries. During decompression those entries are
     * expanded back into a full fixed-size table so the exact same tree-construction code is reused
     * for compression and decompression.</p>
     */
    public static FrequencyTable fromFrequencies(long[] frequencies) {
        FrequencyTable table = new FrequencyTable(frequencies.length);
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
