package com.serafinebot.p4.view;

import com.serafinebot.p4.model.archive.CompressionMode;
import com.serafinebot.p4.model.info.BlockInfo;
import com.serafinebot.p4.util.ByteFormat;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vertical list of Huffman-block rectangles used to pick which block's tree is currently shown.
 */
public final class BlockListPanel extends JPanel {

    private static final Color COLOR_1_BYTE = new Color(77, 139, 211);
    private static final Color COLOR_2_BYTE = new Color(215, 128, 51);
    private static final Color COLOR_STORED = new Color(127, 134, 145);

    private final DefaultListModel<BlockInfo> listModel = new DefaultListModel<>();
    private final JList<BlockInfo> list = new JList<>(listModel);
    private final JLabel header = new JLabel("Cap bloc seleccionat");

    private Consumer<BlockInfo> selectionListener = info -> {
    };

    public BlockListPanel() {
        super(new BorderLayout(0, 6));
        setOpaque(true);
        setBackground(new Color(248, 250, 252));
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(189, 198, 210)), "Blocs"));
        setPreferredSize(new Dimension(220, 580));

        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setBorder(new EmptyBorder(4, 8, 4, 8));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new BlockRenderer());
        list.setBackground(getBackground());
        list.setFixedCellHeight(54);
        list.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            BlockInfo selected = list.getSelectedValue();
            if (selected != null) {
                updateHeader(list.getSelectedIndex(), selected);
                selectionListener.accept(selected);
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 233)));

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void setSelectionListener(Consumer<BlockInfo> listener) {
        this.selectionListener = listener == null ? info -> { } : listener;
    }

    public void setBlocks(List<BlockInfo> blocks) {
        listModel.clear();
        for (BlockInfo block : blocks) {
            listModel.addElement(block);
        }
        if (!blocks.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            header.setText("Cap bloc seleccionat");
        }
    }

    public int getSelectedIndex() {
        return list.getSelectedIndex();
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= listModel.getSize()) {
            return;
        }
        if (list.getSelectedIndex() == index) {
            return;
        }
        list.setSelectedIndex(index);
    }

    public void clear() {
        listModel.clear();
        header.setText("Cap bloc seleccionat");
    }

    private void updateHeader(int index, BlockInfo block) {
        header.setText(String.format("Bloc %d — %s — %s", index + 1, modeLabel(block.mode()), ByteFormat.format(block.blockSize())));
    }

    private static String modeLabel(CompressionMode mode) {
        return switch (mode) {
            case HUFFMAN_1_BYTE -> "1 byte";
            case HUFFMAN_2_BYTE -> "2 bytes";
            case STORED -> "emmagatzemat";
            case HUFFMAN_BLOCK -> "blocs";
            default -> mode.toString();
        };
    }

    private static Color modeColor(CompressionMode mode) {
        return switch (mode) {
            case HUFFMAN_1_BYTE -> COLOR_1_BYTE;
            case HUFFMAN_2_BYTE -> COLOR_2_BYTE;
            default -> COLOR_STORED;
        };
    }

    private static final class BlockRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                       Object value,
                                                       int index,
                                                       boolean isSelected,
                                                       boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof BlockInfo block) {
                Color base = modeColor(block.mode());
                label.setOpaque(true);
                label.setBackground(isSelected ? base.darker() : base);
                label.setForeground(Color.WHITE);
                label.setBorder(new EmptyBorder(8, 12, 8, 12));
                label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
                label.setText(String.format("<html>Bloc %d<br/>%s — %s</html>",
                    index + 1,
                    modeLabel(block.mode()),
                    ByteFormat.format(block.blockSize())));
            }
            return label;
        }
    }
}
