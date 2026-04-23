package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.archive.ArchiveHeader;
import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.info.BlockInfo;
import com.serafinebot.p4.model.info.HuffmanSymbolInfo;
import com.serafinebot.p4.model.info.HuffmanTreeNodeInfo;

import java.util.List;

/**
 * Selected archive representation shared by the codec, writer, and reader.
 *
 * <p>Analysis produces several candidate plans, but only one of them becomes the archive that will
 * be written. This object marks that boundary: the writer receives a selected archive instead of a
 * loose group of mode/plan arguments, and the reader returns the archive-level data reconstructed
 * from disk.</p>
 */
final class HuffmanArchive {

    private final CompressionMode mode;
    private final long originalSize;
    private final ArchiveHeader header;
    private final WholePlan wholePlan;
    private final BlockPlan blockPlan;
    private final List<HuffmanSymbolInfo> symbols;
    private final HuffmanTreeNodeInfo tree;
    private final List<BlockInfo> blocks;

    private HuffmanArchive(CompressionMode mode,
                           long originalSize,
                           ArchiveHeader header,
                           WholePlan wholePlan,
                           BlockPlan blockPlan,
                           List<HuffmanSymbolInfo> symbols,
                           HuffmanTreeNodeInfo tree,
                           List<BlockInfo> blocks) {
        this.mode = mode;
        this.originalSize = originalSize;
        this.header = header;
        this.wholePlan = wholePlan;
        this.blockPlan = blockPlan;
        this.symbols = List.copyOf(symbols);
        this.tree = tree;
        this.blocks = List.copyOf(blocks);
    }

    static HuffmanArchive wholeFile(CompressionMode mode, WholePlan plan) {
        if (mode != CompressionMode.HUFFMAN_1_BYTE && mode != CompressionMode.HUFFMAN_2_BYTE) {
            throw new IllegalArgumentException("El mode no es de fitxer sencer: " + mode);
        }
        return new HuffmanArchive(mode, plan.header().originalSize(), plan.header(), plan, null, List.of(), null, List.of());
    }

    static HuffmanArchive block(BlockPlan plan) {
        return new HuffmanArchive(
            CompressionMode.HUFFMAN_BLOCK,
            plan.header().originalSize(),
            plan.header(),
            null,
            plan,
            List.of(),
            null,
            List.of()
        );
    }

    static HuffmanArchive decoded(ArchiveHeader header,
                                  List<HuffmanSymbolInfo> symbols,
                                  HuffmanTreeNodeInfo tree,
                                  List<BlockInfo> blocks) {
        return new HuffmanArchive(
            header.mode(),
            header.originalSize(),
            header,
            null,
            null,
            symbols,
            tree,
            blocks
        );
    }

    CompressionMode mode() {
        return mode;
    }

    long originalSize() {
        return originalSize;
    }

    ArchiveHeader header() {
        return header;
    }

    List<HuffmanSymbolInfo> symbols() {
        return symbols;
    }

    HuffmanTreeNodeInfo tree() {
        return tree;
    }

    List<BlockInfo> blocks() {
        return blocks;
    }

    long estimatedArchiveSize() {
        return switch (mode) {
            case HUFFMAN_1_BYTE, HUFFMAN_2_BYTE -> requireWholePlan().estimatedArchiveSize();
            case HUFFMAN_BLOCK -> requireBlockPlan().estimatedArchiveSize();
            case STORED, AUTO, HUFFMAN_BLOCK_1_BYTE, HUFFMAN_BLOCK_2_BYTE ->
                throw new IllegalStateException("El mode no te mida estimada de compressio: " + mode);
        };
    }

    WholePlan requireWholePlan() {
        if (wholePlan == null) {
            throw new IllegalStateException("L'arxiu no conte un pla de fitxer sencer.");
        }
        return wholePlan;
    }

    BlockPlan requireBlockPlan() {
        if (blockPlan == null) {
            throw new IllegalStateException("L'arxiu no conte un pla per blocs.");
        }
        return blockPlan;
    }
}
