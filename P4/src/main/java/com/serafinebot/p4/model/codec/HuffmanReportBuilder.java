package com.serafinebot.p4.model.codec;

import com.serafinebot.p4.model.report.HuffmanSymbolInfo;
import com.serafinebot.p4.model.report.HuffmanTreeNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps internal Huffman structures to immutable report DTOs used by the GUI.
 */
final class HuffmanReportBuilder {

    private HuffmanReportBuilder() {
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
