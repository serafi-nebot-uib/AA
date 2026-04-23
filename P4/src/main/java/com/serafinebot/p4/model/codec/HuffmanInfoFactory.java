package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.queue.PriorityQueueStrategy;
import com.serafinebot.p4.model.info.BlockInfo;
import com.serafinebot.p4.model.info.CompressionInfo;
import com.serafinebot.p4.model.info.CompressionStats;
import com.serafinebot.p4.model.info.HuffmanSymbolInfo;
import com.serafinebot.p4.model.info.HuffmanTreeNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps internal Huffman structures to immutable information DTOs used by the GUI.
 */
final class HuffmanInfoFactory {

    private HuffmanInfoFactory() {
    }

    static CompressionInfo compressionInfo(CompressionMode selectedMode,
                                           PriorityQueueStrategy priorityQueueStrategy,
                                           long originalSize,
                                           long archiveSize,
                                           long elapsedMillis,
                                           long treeBuildMillis,
                                           WholePlan bytePlan,
                                           WholePlan wordPlan,
                                           BlockPlan blockPlan) {
        return switch (selectedMode) {
            case HUFFMAN_1_BYTE -> wholeCompressionInfo(
                CompressionMode.HUFFMAN_1_BYTE,
                priorityQueueStrategy,
                originalSize,
                archiveSize,
                elapsedMillis,
                treeBuildMillis,
                bytePlan
            );
            case HUFFMAN_2_BYTE -> wholeCompressionInfo(
                CompressionMode.HUFFMAN_2_BYTE,
                priorityQueueStrategy,
                originalSize,
                archiveSize,
                elapsedMillis,
                treeBuildMillis,
                wordPlan
            );
            case HUFFMAN_BLOCK -> blockCompressionInfo(
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

    private static CompressionInfo wholeCompressionInfo(CompressionMode mode,
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
        return new CompressionInfo(
            stats,
            symbols(plan.table(), plan.leaves()),
            tree(plan.root(), plan.table().totalCount())
        );
    }

    private static CompressionInfo blockCompressionInfo(PriorityQueueStrategy priorityQueueStrategy,
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
        List<BlockInfo> blockInfos = new ArrayList<>(blockPlan.blocks().size());
        for (BlockUnit block : blockPlan.blocks()) {
            blockInfos.add(blockInfo(block));
        }
        return new CompressionInfo(stats, List.of(), null, blockInfos);
    }

    private static BlockInfo blockInfo(BlockUnit block) {
        return switch (block.mode()) {
            case HUFFMAN_1_BYTE -> new BlockInfo(
                block.blockSize(),
                block.mode(),
                tree(block.byteRoot(), block.byteTable().totalCount()),
                symbols(block.byteTable(), block.byteLeaves())
            );
            case HUFFMAN_2_BYTE -> new BlockInfo(
                block.blockSize(),
                block.mode(),
                tree(block.wordRoot(), block.wordTable().totalCount()),
                symbols(block.wordTable(), block.wordLeaves())
            );
            default -> new BlockInfo(block.blockSize(), block.mode(), null, List.of());
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
