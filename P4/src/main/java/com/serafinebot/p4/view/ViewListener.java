package com.serafinebot.p4.view;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.benchmark.BenchmarkConfig;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;

import java.nio.file.Path;
import java.util.Set;

/**
 * Listener implemented by the controller to receive high-level GUI actions.
 */
public interface ViewListener {
    void onCompressRequested(Path inputPath,
                             Path outputPath,
                             PriorityQueueStrategy strategy,
                             CompressionMode preferredMode,
                             Set<CompressionMode> allowedBlockHuffmanModes);

    void onDecompressRequested(Path inputPath, Path outputPath, PriorityQueueStrategy strategy);

    void onBenchmarkRequested(BenchmarkConfig config);
}
