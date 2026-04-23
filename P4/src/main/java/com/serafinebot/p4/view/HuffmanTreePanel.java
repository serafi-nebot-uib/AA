package com.serafinebot.p4.view;

import com.serafinebot.p4.model.info.HuffmanTreeNodeInfo;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Graph-style Huffman tree renderer with pan and zoom interaction.
 */
public final class HuffmanTreePanel extends JPanel {

    private static final int NODE_WIDTH = 240;
    private static final int NODE_MIN_HEIGHT = 84;
    private static final int HORIZONTAL_GAP = 28;
    private static final int VERTICAL_GAP = 128;
    private static final double ZOOM_STEP = 1.12;
    private static final double MIN_SCALE = 0.05;
    private static final double MAX_SCALE = 12.0;
    private static final Stroke EDGE_STROKE = new BasicStroke(1.8f);
    private static final int TEXT_LINE_HEIGHT = 16;
    private static final int TEXT_TOP_PADDING = 18;
    private static final int TEXT_BOTTOM_PADDING = 16;
    private static final int TITLE_SECTION_GAP = 6;
    private static final int CODE_WRAP_CHARS = 26;

    private HuffmanTreeNodeInfo root;
    private LayoutNode layout;
    private String emptyMessage = "No hi ha cap arbre de Huffman disponible.";
    private boolean wordMode = false;
    private double scale = 1.0;
    private double offsetX = 60.0;
    private double offsetY = 40.0;
    private Point dragOrigin;

    public HuffmanTreePanel() {
        setBackground(new Color(248, 250, 252));
        setOpaque(true);
        setPreferredSize(new Dimension(960, 580));

        MouseInputAdapter mouseHandler = new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragOrigin = event.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragOrigin == null) {
                    return;
                }
                offsetX += event.getX() - dragOrigin.x;
                offsetY += event.getY() - dragOrigin.y;
                dragOrigin = event.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragOrigin = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                double factor = event.getPreciseWheelRotation() < 0 ? ZOOM_STEP : (1.0 / ZOOM_STEP);
                zoom(factor, event.getPoint().x, event.getPoint().y);
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
    }

    public void setTree(HuffmanTreeNodeInfo root, String emptyMessage) {
        setTree(root, emptyMessage, false);
    }

    public void setTree(HuffmanTreeNodeInfo root, String emptyMessage, boolean wordMode) {
        this.root = root;
        this.emptyMessage = emptyMessage;
        this.wordMode = wordMode;
        this.layout = root == null ? null : buildLayout(root, 0.0, 0);
        fitToView();
        SwingUtilities.invokeLater(this::fitToView);
    }

    public void zoomIn() {
        zoom(ZOOM_STEP, getWidth() / 2.0, getHeight() / 2.0);
    }

    public void zoomOut() {
        zoom(1.0 / ZOOM_STEP, getWidth() / 2.0, getHeight() / 2.0);
    }

    public void resetView() {
        fitToView();
    }

    private void fitToView() {
        if (layout == null) {
            scale = 1.0;
            offsetX = 60.0;
            offsetY = 40.0;
            repaint();
            return;
        }
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0) {
            viewWidth = getPreferredSize().width;
        }
        if (viewHeight <= 0) {
            viewHeight = getPreferredSize().height;
        }
        double padding = 40.0;
        double treeWidth = layout.subtreeWidth;
        double treeHeight = computeTreeHeight(layout);
        double availableWidth = Math.max(1.0, viewWidth - 2 * padding);
        double availableHeight = Math.max(1.0, viewHeight - 2 * padding);
        double fitScale = Math.min(availableWidth / treeWidth, availableHeight / treeHeight);
        scale = clamp(fitScale, MIN_SCALE, MAX_SCALE);
        double treeCenterX = treeWidth / 2.0;
        double treeCenterY = treeHeight / 2.0;
        offsetX = viewWidth / 2.0 - treeCenterX * scale;
        offsetY = viewHeight / 2.0 - treeCenterY * scale;
        repaint();
    }

    private double computeTreeHeight(LayoutNode node) {
        double bottom = node.y + node.height / 2.0;
        if (node.zeroChild != null) {
            bottom = Math.max(bottom, computeTreeHeight(node.zeroChild));
        }
        if (node.oneChild != null) {
            bottom = Math.max(bottom, computeTreeHeight(node.oneChild));
        }
        return bottom;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (layout == null) {
            g2.setColor(new Color(109, 122, 138));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));
            FontMetrics metrics = g2.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(emptyMessage)) / 2;
            int y = getHeight() / 2;
            g2.drawString(emptyMessage, Math.max(20, x), y);
            g2.dispose();
            return;
        }

        AffineTransform original = g2.getTransform();
        g2.translate(offsetX, offsetY);
        g2.scale(scale, scale);
        drawEdges(g2, layout);
        drawNodes(g2, layout);
        g2.setTransform(original);
        g2.dispose();
    }

    private void zoom(double factor, double anchorX, double anchorY) {
        double nextScale = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
        factor = nextScale / scale;
        offsetX = anchorX - (anchorX - offsetX) * factor;
        offsetY = anchorY - (anchorY - offsetY) * factor;
        scale = nextScale;
        repaint();
    }

    private void drawEdges(Graphics2D g2, LayoutNode node) {
        g2.setStroke(EDGE_STROKE);
        g2.setColor(new Color(112, 126, 145));

        if (node.zeroChild != null) {
            drawEdge(g2, node, node.zeroChild, "0");
            drawEdges(g2, node.zeroChild);
        }
        if (node.oneChild != null) {
            drawEdge(g2, node, node.oneChild, "1");
            drawEdges(g2, node.oneChild);
        }
    }

    private void drawEdge(Graphics2D g2, LayoutNode parent, LayoutNode child, String label) {
        double x1 = parent.x;
        double y1 = parent.y + parent.height / 2.0;
        double x2 = child.x;
        double y2 = child.y - child.height / 2.0;
        g2.draw(new Line2D.Double(x1, y1, x2, y2));

        double midX = (x1 + x2) / 2.0;
        double midY = (y1 + y2) / 2.0;
        RoundRectangle2D marker = new RoundRectangle2D.Double(midX - 10.0, midY - 11.0, 20.0, 20.0, 10.0, 10.0);
        g2.setColor(new Color(214, 224, 255));
        g2.fill(marker);
        g2.setColor(new Color(72, 94, 144));
        g2.draw(marker);
        Font oldFont = g2.getFont();
        g2.setFont(oldFont.deriveFont(Font.BOLD, 12f));
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(label, (float) (midX - metrics.stringWidth(label) / 2.0), (float) (midY + metrics.getAscent() / 2.8));
        g2.setFont(oldFont);
        g2.setColor(new Color(112, 126, 145));
    }

    private void drawNodes(Graphics2D g2, LayoutNode node) {
        drawNode(g2, node);
        if (node.zeroChild != null) {
            drawNodes(g2, node.zeroChild);
        }
        if (node.oneChild != null) {
            drawNodes(g2, node.oneChild);
        }
    }

    private void drawNode(Graphics2D g2, LayoutNode node) {
        double left = node.x - node.width / 2.0;
        double top = node.y - node.height / 2.0;
        RoundRectangle2D box = new RoundRectangle2D.Double(left, top, node.width, node.height, 18.0, 18.0);

        Color fill = node.info.leaf() ? new Color(227, 240, 255) : new Color(238, 242, 247);
        Color border = node.info.leaf() ? new Color(66, 120, 198) : new Color(105, 114, 126);
        g2.setColor(fill);
        g2.fill(box);
        g2.setColor(border);
        g2.draw(box);

        Font titleFont = getFont().deriveFont(Font.BOLD, 18.5f);
        Font infoFont = getFont().deriveFont(Font.PLAIN, 16.5f);
        Font codeFont = new Font(Font.MONOSPACED, Font.PLAIN, 16);

        double baselineY = top + TEXT_TOP_PADDING;
        for (int i = 0; i < node.lines.size(); i++) {
            String line = node.lines.get(i);
            Font font = i == 0 ? titleFont : (line.startsWith("codi=") || line.startsWith("  ") ? codeFont : infoFont);
            double lineY = baselineY + i * TEXT_LINE_HEIGHT + (i >= 1 ? TITLE_SECTION_GAP : 0);
            drawCenteredLine(g2, font, line, node.x, lineY);
        }
    }

    private LayoutNode buildLayout(HuffmanTreeNodeInfo info, double leftEdge, int depth) {
        List<String> lines = buildLines(info);
        double nodeHeight = Math.max(
            NODE_MIN_HEIGHT,
            TEXT_TOP_PADDING + lines.size() * TEXT_LINE_HEIGHT + (lines.size() > 1 ? TITLE_SECTION_GAP : 0) + TEXT_BOTTOM_PADDING
        );
        double y = depth * VERTICAL_GAP + nodeHeight / 2.0;
        if (info.leaf()) {
            double x = leftEdge + NODE_WIDTH / 2.0;
            return new LayoutNode(info, x, y, NODE_WIDTH, nodeHeight, NODE_WIDTH, lines, null, null);
        }

        LayoutNode zeroChild = buildLayout(info.zeroChild(), leftEdge, depth + 1);
        LayoutNode oneChild = buildLayout(info.oneChild(), leftEdge + zeroChild.subtreeWidth + HORIZONTAL_GAP, depth + 1);
        double subtreeWidth = Math.max(NODE_WIDTH, zeroChild.subtreeWidth + HORIZONTAL_GAP + oneChild.subtreeWidth);
        double x = (zeroChild.x + oneChild.x) / 2.0;
        return new LayoutNode(info, x, y, NODE_WIDTH, nodeHeight, subtreeWidth, lines, zeroChild, oneChild);
    }

    private List<String> buildLines(HuffmanTreeNodeInfo info) {
        List<String> lines = new ArrayList<>();
        lines.add(info.leaf() ? formatSymbol(info.symbol()) : "freq=" + info.frequency());
        lines.add(info.leaf()
            ? String.format("%s • freq=%d", formatHex(info.symbol()), info.frequency())
            : String.format("min=%s • freq=%d", formatHex(info.symbol()), info.frequency()));
        lines.add("p=" + formatProbability(info.probability()));

        String codeLine = "codi=" + displayCode(info.code());
        if (codeLine.length() <= CODE_WRAP_CHARS) {
            lines.add(codeLine);
            return lines;
        }

        lines.add(codeLine.substring(0, CODE_WRAP_CHARS));
        String remainder = codeLine.substring(CODE_WRAP_CHARS);
        while (!remainder.isEmpty()) {
            int chunkLength = Math.min(CODE_WRAP_CHARS - 2, remainder.length());
            lines.add("  " + remainder.substring(0, chunkLength));
            remainder = remainder.substring(chunkLength);
        }
        return lines;
    }

    private String formatSymbol(int symbol) {
        if (wordMode) {
            return String.format("parella[%02X %02X]", (symbol >>> 8) & 0xFF, symbol & 0xFF);
        }
        if (symbol > 0xFF) {
            return String.format("parella[%02X %02X]", (symbol >>> 8) & 0xFF, symbol & 0xFF);
        }
        return switch (symbol) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case ' ' -> "<espai>";
            default -> symbol >= 32 && symbol <= 126
                ? Character.toString((char) symbol)
                : String.format("byte[%d]", symbol);
        };
    }

    private String formatHex(int symbol) {
        if (wordMode) {
            return String.format("0x%04X", symbol & 0xFFFF);
        }
        if (symbol <= 0xFF) {
            return String.format("0x%02X", symbol);
        }
        return String.format("0x%04X", symbol);
    }

    private void drawCenteredLine(Graphics2D g2, Font font, String text, double centerX, double baselineY) {
        Font oldFont = g2.getFont();
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(text, (float) (centerX - metrics.stringWidth(text) / 2.0), (float) baselineY);
        g2.setFont(oldFont);
    }

    private String displayCode(String code) {
        return code.isEmpty() ? "<arrel>" : code;
    }

    private String formatProbability(double probability) {
        return String.format("%.6f", probability);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class LayoutNode {
        private final HuffmanTreeNodeInfo info;
        private final double x;
        private final double y;
        private final double width;
        private final double height;
        private final double subtreeWidth;
        private final List<String> lines;
        private final LayoutNode zeroChild;
        private final LayoutNode oneChild;

        private LayoutNode(HuffmanTreeNodeInfo info,
                           double x,
                           double y,
                           double width,
                           double height,
                           double subtreeWidth,
                           List<String> lines,
                           LayoutNode zeroChild,
                           LayoutNode oneChild) {
            this.info = info;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.subtreeWidth = subtreeWidth;
            this.lines = lines;
            this.zeroChild = zeroChild;
            this.oneChild = oneChild;
        }
    }
}
