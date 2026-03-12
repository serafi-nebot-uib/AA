package com.serafinebot.p2.view;

import com.serafinebot.p2.model.PieceType;
import com.serafinebot.p2.model.Position;

import java.util.List;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;

/**
 * Panel that displays the chess board with visited cells, sequence numbers,
 * piece positions and step highlighting.
 * <p>
 * The board is always rendered as a perfect square (based on the smaller
 * of the available width and height), centred within the panel's bounds.
 * An inner JPanel with GridLayout holds the cells; this outer panel
 * handles the square-constraint layout.
 */
public class BoardPanel extends JPanel {

    // Board colours
    private static final Color LIGHT_CELL = Color.WHITE;
    private static final Color DARK_CELL = new Color(139, 69, 19);           // Brown
    private static final Color VISITED_LIGHT = new Color(144, 238, 144);     // Light green
    private static final Color VISITED_DARK = new Color(34, 139, 34);        // Dark green
    private static final Color HIGHLIGHT_BORDER = new Color(255, 215, 0);    // Gold
    private static final Color SEQ_NUMBER_COLOR = Color.BLACK;
    private static final Color SEQ_NUMBER_SHADOW = new Color(255, 255, 255, 160);

    private int rows;
    private int cols;
    private CellPanel[][] cells;

    /** Inner panel that holds the grid; sized to a perfect square. */
    private final JPanel gridPanel;

    /** Callback invoked when a cell is clicked: (row, col). */
    private BiConsumer<Integer, Integer> onCellClicked;

    /**
     * Create a BoardPanel with default 8x8 dimensions.
     */
    public BoardPanel() {
        setLayout(null); // We manually position the inner grid panel
        setPreferredSize(new Dimension(500, 500));
        setMinimumSize(new Dimension(200, 200));
        setBackground(new Color(60, 60, 60)); // subtle background behind grid

        gridPanel = new JPanel();
        gridPanel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
        add(gridPanel);

        initBoard(8, 8);
    }

    /**
     * Lay out the inner grid panel as a centred square.
     */
    @Override
    public void doLayout() {
        super.doLayout();
        Insets insets = getInsets();
        int availW = getWidth() - insets.left - insets.right;
        int availH = getHeight() - insets.top - insets.bottom;
        int side = Math.min(availW, availH);
        int x = insets.left + (availW - side) / 2;
        int y = insets.top + (availH - side) / 2;
        gridPanel.setBounds(x, y, side, side);
    }

    /**
     * Initialise or re-initialise the board grid with new dimensions.
     *
     * @param rows Number of rows
     * @param cols Number of columns
     */
    public void setBoard(int rows, int cols) {
        initBoard(rows, cols);
        revalidate();
        repaint();
    }

    /**
     * Build the grid of CellPanel instances.
     */
    private void initBoard(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.cells = new CellPanel[rows][cols];

        gridPanel.removeAll();
        gridPanel.setLayout(new GridLayout(rows, cols));

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                CellPanel cell = new CellPanel(r, c);
                final int clickRow = r;
                final int clickCol = c;
                cell.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (onCellClicked != null) {
                            onCellClicked.accept(clickRow, clickCol);
                        }
                    }
                });
                cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cells[r][c] = cell;
                gridPanel.add(cell);
            }
        }
    }

    /**
     * Update the board to show the solution up to a given step.
     * Cells 0..currentStep are marked as visited with sequence numbers.
     * The cell at currentStep is highlighted.
     * <p>
     * Piece ownership is determined by step parity: even steps belong to piece 1,
     * odd steps belong to piece 2 (they strictly alternate from the start).
     *
     * @param path        The solution as an ordered list of positions
     * @param currentStep The step to display up to (0-based, inclusive)
     * @param piece1Type  Type of piece 1 (for icon rendering)
     * @param piece2Type  Type of piece 2 (for icon rendering)
     */
    public void updateBoard(List<Position> path, int currentStep,
                            PieceType piece1Type, PieceType piece2Type) {
        clearCells();

        if (path == null || path.isEmpty()) return;

        int limit = Math.min(currentStep, path.size() - 1);

        // Track latest position of each piece
        Position piece1Pos = null;
        Position piece2Pos = null;

        for (int i = 0; i <= limit; i++) {
            Position pos = path.get(i);
            CellPanel cell = cells[pos.row()][pos.col()];
            cell.setVisited(true);
            cell.setSequenceNumber(i + 1);  // 1-based display

            // Even steps = piece 1 (index 0), odd steps = piece 2 (index 1)
            if (i % 2 == 0) {
                piece1Pos = pos;
            } else {
                piece2Pos = pos;
            }
        }

        // Highlight current step cell
        Position highlightPos = path.get(limit);
        cells[highlightPos.row()][highlightPos.col()].setHighlighted(true);

        // Place piece icons at their latest positions
        if (piece1Pos != null) {
            cells[piece1Pos.row()][piece1Pos.col()].setPiece(piece1Type, true);
        }
        if (piece2Pos != null) {
            cells[piece2Pos.row()][piece2Pos.col()].setPiece(piece2Type, false);
        }

        repaint();
    }

    /**
     * Show the complete solution with all cells visited.
     *
     * @param path       The complete solution as an ordered list of positions
     * @param piece1Type Type of piece 1
     * @param piece2Type Type of piece 2
     */
    public void showFullSolution(List<Position> path,
                                 PieceType piece1Type, PieceType piece2Type) {
        if (path == null || path.isEmpty()) return;
        updateBoard(path, path.size() - 1, piece1Type, piece2Type);
        // Remove highlight for full solution view
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c].setHighlighted(false);
            }
        }
        repaint();
    }

    /**
     * Reset all cells to their initial empty state.
     */
    public void reset() {
        clearCells();
        repaint();
    }

    /**
     * Show a preview of the two pieces at their starting positions
     * on an otherwise empty board.
     *
     * @param piece1Type Type of piece 1
     * @param piece1Row  Starting row of piece 1
     * @param piece1Col  Starting column of piece 1
     * @param piece2Type Type of piece 2
     * @param piece2Row  Starting row of piece 2
     * @param piece2Col  Starting column of piece 2
     */
    public void showPiecePreview(PieceType piece1Type, int piece1Row, int piece1Col,
                                 PieceType piece2Type, int piece2Row, int piece2Col) {
        clearCells();
        if (piece1Row >= 0 && piece1Row < rows && piece1Col >= 0 && piece1Col < cols) {
            cells[piece1Row][piece1Col].setPiece(piece1Type, true);
            cells[piece1Row][piece1Col].setHighlighted(true);
        }
        if (piece2Row >= 0 && piece2Row < rows && piece2Col >= 0 && piece2Col < cols) {
            cells[piece2Row][piece2Col].setPiece(piece2Type, false);
            cells[piece2Row][piece2Col].setHighlighted(true);
        }
        repaint();
    }

    /**
     * Register a callback invoked when a board cell is clicked.
     *
     * @param listener Receives (row, col) of the clicked cell
     */
    public void setOnCellClicked(BiConsumer<Integer, Integer> listener) {
        this.onCellClicked = listener;
    }

    /**
     * Clear visited/highlighted/piece state on all cells.
     */
    private void clearCells() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c].setVisited(false);
                cells[r][c].setSequenceNumber(-1);
                cells[r][c].setHighlighted(false);
                cells[r][c].setPiece(null, true);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  CellPanel — inner class representing a single board cell
    // -------------------------------------------------------------------------

    /**
     * Inner panel representing one cell on the chess board.
     * Draws background, sequence number, piece icon and highlight.
     */
    private static class CellPanel extends JPanel {

        private final int row;
        private final int col;
        private boolean visited;
        private int sequenceNumber = -1;
        private boolean highlighted;
        private PieceType piece;
        private boolean pieceIsWhite;

        CellPanel(int row, int col) {
            this.row = row;
            this.col = col;
            setPreferredSize(new Dimension(60, 60));
        }

        void setVisited(boolean visited) {
            this.visited = visited;
        }

        void setSequenceNumber(int num) {
            this.sequenceNumber = num;
        }

        void setHighlighted(boolean highlighted) {
            this.highlighted = highlighted;
        }

        void setPiece(PieceType piece, boolean isWhite) {
            this.piece = piece;
            this.pieceIsWhite = isWhite;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int w = getWidth();
            int h = getHeight();

            // 1. Draw background (checkerboard pattern)
            boolean isLightCell = (row + col) % 2 == 0;
            Color bg;
            if (visited) {
                bg = isLightCell ? VISITED_LIGHT : VISITED_DARK;
            } else {
                bg = isLightCell ? LIGHT_CELL : DARK_CELL;
            }
            g2.setColor(bg);
            g2.fillRect(0, 0, w, h);

            // 2. Draw sequence number (top-left corner)
            if (visited && sequenceNumber > 0) {
                int fontSize = Math.max(10, Math.min(w, h) / 4);
                g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                String numStr = String.valueOf(sequenceNumber);
                FontMetrics fm = g2.getFontMetrics();

                int tx = 3;
                int ty = fm.getAscent() + 2;

                // Shadow for readability
                g2.setColor(SEQ_NUMBER_SHADOW);
                g2.drawString(numStr, tx + 1, ty + 1);
                g2.setColor(SEQ_NUMBER_COLOR);
                g2.drawString(numStr, tx, ty);
            }

            // 3. Draw piece icon
            if (piece != null) {
                drawPieceIcon(g2, w, h);
            }

            // 4. Draw highlight border (current step)
            if (highlighted) {
                g2.setColor(HIGHLIGHT_BORDER);
                g2.setStroke(new BasicStroke(3f));
                g2.drawRect(1, 1, w - 3, h - 3);
            }

            g2.dispose();
        }

        /**
         * Draw the piece icon centred in the cell.
         * Uses PieceIcon's image loading/cache, or a text fallback.
         */
        private void drawPieceIcon(Graphics2D g2, int w, int h) {
            int padding = Math.max(4, Math.min(w, h) / 8);
            int size = Math.min(w, h) - 2 * padding;
            int x = (w - size) / 2;
            int y = (h - size) / 2;

            // Try to load image from cache
            String path = piece.getImagePath(pieceIsWhite);
            java.awt.image.BufferedImage img = null;
            try {
                java.io.InputStream is = getClass().getResourceAsStream(path);
                if (is != null) {
                    img = javax.imageio.ImageIO.read(is);
                    is.close();
                }
            } catch (Exception ignored) {
                // Fall through to text rendering
            }

            if (img != null) {
                g2.drawImage(img, x, y, size, size, null);
            } else {
                // Text fallback
                Color bg = pieceIsWhite ? new Color(240, 240, 220, 200) : new Color(80, 60, 40, 200);
                Color fg = pieceIsWhite ? Color.BLACK : Color.WHITE;
                g2.setColor(bg);
                g2.fillOval(x, y, size, size);
                g2.setColor(fg);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x, y, size, size);

                String label = piece.getShortName();
                g2.setFont(new Font("SansSerif", Font.BOLD, size / 2));
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(label)) / 2;
                int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, tx, ty);
            }
        }
    }
}
