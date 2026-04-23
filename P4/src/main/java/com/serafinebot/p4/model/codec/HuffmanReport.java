package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.report.BlockReport;
import com.serafinebot.p4.model.report.CompressionReport;
import com.serafinebot.p4.model.report.CompressionStats;
import com.serafinebot.p4.model.report.HuffmanSymbolInfo;
import com.serafinebot.p4.model.report.HuffmanTreeNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps internal Huffman structures to immutable report DTOs used by the GUI.
 */
final class HuffmanReport {

    private HuffmanReport() {
    }

    static CompressionReport compressionReport(CompressionMode selectedMode,
                                               PriorityQueueStrategy priorityQueueStrategy,
                                               long originalSize,
                                               long archiveSize,
                                               long elapsedMillis,
                                               long treeBuildMillis,
                                               WholePlan bytePlan,
                                               WholePlan wordPlan,
                                               BlockPlan blockPlan) {
        return switch (selectedMode) {
            case STORED -> storedCompressionReport(
                priorityQueueStrategy,
                originalSize,
                archiveSize,
                elapsedMillis,
                treeBuildMillis,
                bytePlan
            );
            case HUFFMAN_1_BYTE -> wholeCompressionReport(
                CompressionMode.HUFFMAN_1_BYTE,
                priorityQueueStrategy,
                originalSize,
                archiveSize,
                elapsedMillis,
                treeBuildMillis,
                bytePlan
            );
            case HUFFMAN_2_BYTE -> wholeCompressionReport(
                CompressionMode.HUFFMAN_2_BYTE,
                priorityQueueStrategy,
                originalSize,
                archiveSize,
                elapsedMillis,
                treeBuildMillis,
                wordPlan
            );
            case HUFFMAN_BLOCK -> blockCompressionReport(
                priorityQueueStrategy,
                originalSize,
                archiveSize,
                elapsedMillis,
                treeBuildMillis,
                blockPlan,
                bytePlan
            );
            default -> throw new IllegalStateException("Mode d'arxiu seleccionat no valid: " + selectedMode);
        };
    }

    static List<HuffmanSymbolInfo> symbols(FrequencyTable table, HuffmanCode[] leaves) {
        if (table.distinctSymbolCount() == 0) {
            return List.of();
        }

        double totalCount = table.totalCount();
        List<HuffmanSymbolInfo> symbols = new ArrayList<>(table.distinctSymbolCount());
        for (int symbol = 0; symbol < table.symbolSpaceSize(); symbol++) {
            long frequency = table.frequencyOf(symbol);
            if (frequency == 0L) {
                continue;
            }
            symbols.add(new HuffmanSymbolInfo(symbol, frequency, frequency / totalCount, codeString(leaves[symbol].code())));
        }
        return List.copyOf(symbols);
    }

    static HuffmanTreeNodeInfo tree(HuffmanCode root, long totalCount) {
        return tree(root, totalCount, "");
    }

    private static CompressionReport storedCompressionReport(PriorityQueueStrategy priorityQueueStrategy,
                                                             long originalSize,
                                                             long archiveSize,
                                                             long elapsedMillis,
                                                             long treeBuildMillis,
                                                             WholePlan bytePlan) {
        CompressionStats stats = new CompressionStats(
            CompressionMode.STORED,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            ArchiveHeader.stored(originalSize).sizeInBytes(),
            bytePlan == null ? 0 : bytePlan.table().distinctSymbolCount(),
            bytePlan == null ? 0L : bytePlan.bitCount(),
            bytePlan == null ? 0.0 : bytePlan.entropy(),
            bytePlan == null ? 0.0 : bytePlan.averageCodeLength(),
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(
            stats,
            bytePlan == null ? List.of() : symbols(bytePlan.table(), bytePlan.leaves()),
            bytePlan == null ? null : tree(bytePlan.root(), bytePlan.table().totalCount())
        );
    }

    private static CompressionReport wholeCompressionReport(CompressionMode mode,
                                                            PriorityQueueStrategy priorityQueueStrategy,
                                                            long originalSize,
                                                            long archiveSize,
                                                            long elapsedMillis,
                                                            long treeBuildMillis,
                                                            WholePlan plan) {
        CompressionStats stats = new CompressionStats(
            mode,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            plan.metadataOverhead(),
            plan.table().distinctSymbolCount(),
            plan.bitCount(),
            plan.entropy(),
            plan.averageCodeLength(),
            elapsedMillis,
            treeBuildMillis
        );
        return new CompressionReport(
            stats,
            symbols(plan.table(), plan.leaves()),
            tree(plan.root(), plan.table().totalCount())
        );
    }

    private static CompressionReport blockCompressionReport(PriorityQueueStrategy priorityQueueStrategy,
                                                            long originalSize,
                                                            long archiveSize,
                                                            long elapsedMillis,
                                                            long treeBuildMillis,
                                                            BlockPlan blockPlan,
                                                            WholePlan bytePlan) {
        CompressionStats stats = new CompressionStats(
            CompressionMode.HUFFMAN_BLOCK,
            priorityQueueStrategy,
            originalSize,
            archiveSize,
            blockPlan.metadataOverhead(),
            bytePlan == null ? 0 : bytePlan.table().distinctSymbolCount(),
            blockPlan.totalCompressedBits(),
            bytePlan == null ? 0.0 : bytePlan.entropy(),
            blockPlan.averageCodeLength(),
            elapsedMillis,
            treeBuildMillis
        );
        List<BlockReport> blockReports = new ArrayList<>(blockPlan.blocks().size());
        for (BlockUnit block : blockPlan.blocks()) {
            blockReports.add(blockReport(block));
        }
        return new CompressionReport(stats, List.of(), null, blockReports);
    }

    private static BlockReport blockReport(BlockUnit block) {
        return switch (block.mode()) {
            case HUFFMAN_1_BYTE -> new BlockReport(
                block.blockSize(),
                block.mode(),
                tree(block.byteRoot(), block.byteTable().totalCount()),
                symbols(block.byteTable(), block.byteLeaves())
            );
            case HUFFMAN_2_BYTE -> new BlockReport(
                block.blockSize(),
                block.mode(),
                tree(block.wordRoot(), block.wordTable().totalCount()),
                symbols(block.wordTable(), block.wordLeaves())
            );
            default -> new BlockReport(block.blockSize(), block.mode(), null, List.of());
        };
    }

    private static HuffmanTreeNodeInfo tree(HuffmanCode node, long totalCount, String code) {
        if (node == null) {
            return null;
        }
        return new HuffmanTreeNodeInfo(
            node.symbol(),
            node.frequency(),
            totalCount == 0L ? 0.0 : node.frequency() / (double) totalCount,
            code,
            node.isLeaf(),
            tree(node.min(), totalCount, code + '0'),
            tree(node.max(), totalCount, code + '1')
        );
    }

    private static String codeString(byte[] code) {
        if (code.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(code.length);
        for (byte bit : code) {
            builder.append(bit == 0 ? '0' : '1');
        }
        return builder.toString();
    }
}
