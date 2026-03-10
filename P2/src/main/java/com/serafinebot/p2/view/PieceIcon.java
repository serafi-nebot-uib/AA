package com.serafinebot.p2.view;

import com.serafinebot.p2.model.PieceType;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Reusable component that renders a scaled piece image.
 * Loads images from resources and caches them for performance.
 * Handles missing images gracefully by drawing a text fallback.
 */
public class PieceIcon extends JPanel {

    private static final int PADDING = 4;

    /** Cache loaded images to avoid repeated I/O. Key = resource path. */
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();

    private PieceType pieceType;
    private boolean isWhite;
    private BufferedImage image;

    /**
     * Create a PieceIcon for the given piece and colour.
     *
     * @param pieceType The piece type to display
     * @param isWhite   true for white variant, false for black
     */
    public PieceIcon(PieceType pieceType, boolean isWhite) {
        this.pieceType = pieceType;
        this.isWhite = isWhite;
        setOpaque(false);
        setPreferredSize(new Dimension(48, 48));
        loadImage();
    }

    /**
     * Create an empty PieceIcon (no piece displayed).
     */
    public PieceIcon() {
        this.pieceType = null;
        this.isWhite = true;
        setOpaque(false);
        setPreferredSize(new Dimension(48, 48));
    }

    /**
     * Change the displayed piece.
     *
     * @param pieceType New piece type (null to clear)
     * @param isWhite   true for white variant
     */
    public void setPiece(PieceType pieceType, boolean isWhite) {
        this.pieceType = pieceType;
        this.isWhite = isWhite;
        loadImage();
        repaint();
    }

    /**
     * Load the image for the current piece from resources.
     * Falls back to null (text rendering) if image is missing.
     */
    private void loadImage() {
        if (pieceType == null) {
            image = null;
            return;
        }

        String path = pieceType.getImagePath(isWhite);
        image = IMAGE_CACHE.get(path);

        if (image == null) {
            try {
                InputStream is = getClass().getResourceAsStream(path);
                if (is != null) {
                    image = ImageIO.read(is);
                    IMAGE_CACHE.put(path, image);
                    is.close();
                }
            } catch (Exception e) {
                // Image not found or unreadable — will use text fallback
                image = null;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (pieceType == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth() - 2 * PADDING;
        int h = getHeight() - 2 * PADDING;
        int size = Math.min(w, h);

        int x = PADDING + (w - size) / 2;
        int y = PADDING + (h - size) / 2;

        if (image != null) {
            // Draw scaled image centred in the component
            g2.drawImage(image, x, y, size, size, null);
        } else {
            // Fallback: draw short name text inside a circle
            drawTextFallback(g2, x, y, size);
        }

        g2.dispose();
    }

    /**
     * Draw a text-based fallback when the piece image is unavailable.
     */
    private void drawTextFallback(Graphics2D g2, int x, int y, int size) {
        Color bg = isWhite ? new Color(240, 240, 220) : new Color(80, 60, 40);
        Color fg = isWhite ? Color.BLACK : Color.WHITE;

        g2.setColor(bg);
        g2.fillOval(x, y, size, size);
        g2.setColor(fg);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(x, y, size, size);

        String label = pieceType.getShortName();
        g2.setFont(new Font("SansSerif", Font.BOLD, size / 2));
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (size - fm.stringWidth(label)) / 2;
        int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(label, tx, ty);
    }

    /**
     * Clear the image cache (useful if resources are reloaded).
     */
    public static void clearCache() {
        IMAGE_CACHE.clear();
    }

    public PieceType getPieceType() {
        return pieceType;
    }

    public boolean isWhitePiece() {
        return isWhite;
    }
}
