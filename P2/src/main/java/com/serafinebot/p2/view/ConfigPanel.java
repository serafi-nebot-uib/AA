package com.serafinebot.p2.view;

import com.serafinebot.p2.model.PieceType;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * Configuration panel for the Hamiltonian path solver.
 * Allows the user to set board dimensions, piece types, initial positions
 * and displays time prediction / difficulty estimates.
 * <p>
 * Implements {@link Scrollable} so that the enclosing JScrollPane uses the
 * viewport width (preventing horizontal overflow) while allowing vertical
 * scrolling when the panel is taller than the available space.
 * <p>
 * All GUI labels are in Catalan.
 */
public class ConfigPanel extends JPanel implements Scrollable {

    // ---- Board size controls ----
    private final JSpinner rowsSpinner;
    private final JSpinner colsSpinner;

    // ---- Piece 1 controls ----
    private final JComboBox<PieceType> piece1Combo;
    private final PieceIcon piece1Icon;
    private final JSpinner piece1Row;
    private final JSpinner piece1Col;

    // ---- Piece 2 controls ----
    private final JComboBox<PieceType> piece2Combo;
    private final PieceIcon piece2Icon;
    private final JSpinner piece2Row;
    private final JSpinner piece2Col;

    // ---- Placement mode buttons ----
    private final JToggleButton place1Button;
    private final JToggleButton place2Button;
    private final ButtonGroup placementGroup;

    // ---- Prediction labels ----
    private final JLabel predictionTimeLabel;
    private final JLabel difficultyLabel;

    // ---- Listener for external recalculation ----
    private Runnable onConfigChanged;

    /**
     * Build the configuration panel.
     */
    public ConfigPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // =====================================================================
        // Board size section
        // =====================================================================
        row = addSectionHeader(gbc, row, "Mida del tauler");

        rowsSpinner = createBoundedSpinner(4, 12, 8);
        colsSpinner = createBoundedSpinner(4, 12, 8);

        row = addLabelledSpinner(gbc, row, "Files:", rowsSpinner);
        row = addLabelledSpinner(gbc, row, "Columnes:", colsSpinner);
        row = addSeparator(gbc, row);

        // =====================================================================
        // Piece 1 section
        // =====================================================================
        row = addSectionHeader(gbc, row, "Peça 1 (blanca)");

        piece1Combo = new JComboBox<>(PieceType.values());
        piece1Combo.setToolTipText("Selecciona el tipus de peça 1");
        piece1Icon = new PieceIcon(PieceType.KNIGHT, true);

        row = addLabelledComponent(gbc, row, "Tipus:", piece1Combo);
        row = addCentredComponent(gbc, row, piece1Icon);

        piece1Row = createBoundedSpinner(0, 11, 0);
        piece1Col = createBoundedSpinner(0, 11, 0);
        row = addPositionRow(gbc, row, "Posició inicial:", piece1Row, piece1Col);

        place1Button = new JToggleButton("\u2295 Col\u00b7locar al tauler");
        place1Button.setToolTipText("Fes clic a una casella del tauler per situar la peça 1");
        place1Button.setFocusPainted(false);
        row = addCentredComponent(gbc, row, place1Button);
        row = addSeparator(gbc, row);

        // =====================================================================
        // Piece 2 section
        // =====================================================================
        row = addSectionHeader(gbc, row, "Peça 2 (negra)");

        piece2Combo = new JComboBox<>(PieceType.values());
        piece2Combo.setToolTipText("Selecciona el tipus de peça 2");
        piece2Icon = new PieceIcon(PieceType.KNIGHT, false);

        row = addLabelledComponent(gbc, row, "Tipus:", piece2Combo);
        row = addCentredComponent(gbc, row, piece2Icon);

        piece2Row = createBoundedSpinner(0, 11, 0);
        piece2Col = createBoundedSpinner(0, 11, 1);
        row = addPositionRow(gbc, row, "Posició inicial:", piece2Row, piece2Col);

        place2Button = new JToggleButton("\u2295 Col\u00b7locar al tauler");
        place2Button.setToolTipText("Fes clic a una casella del tauler per situar la peça 2");
        place2Button.setFocusPainted(false);
        row = addCentredComponent(gbc, row, place2Button);
        row = addSeparator(gbc, row);

        // Only one placement mode at a time (mutual exclusion)
        placementGroup = new ButtonGroup();
        placementGroup.add(place1Button);
        placementGroup.add(place2Button);

        // =====================================================================
        // Prediction section
        // =====================================================================
        row = addSectionHeader(gbc, row, "Predicció de temps");

        predictionTimeLabel = new JLabel("--");
        predictionTimeLabel.setFont(predictionTimeLabel.getFont().deriveFont(Font.BOLD));
        row = addLabelledComponent(gbc, row, "Temps estimat:", predictionTimeLabel);

        difficultyLabel = new JLabel("--");
        difficultyLabel.setFont(difficultyLabel.getFont().deriveFont(Font.BOLD));
        row = addLabelledComponent(gbc, row, "Dificultat:", difficultyLabel);

        // Vertical glue to push everything to the top
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(Box.createVerticalGlue(), gbc);

        // =====================================================================
        // Listeners
        // =====================================================================
        ChangeListener sizeChanged = e -> onBoardSizeChanged();
        rowsSpinner.addChangeListener(sizeChanged);
        colsSpinner.addChangeListener(sizeChanged);

        piece1Combo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                piece1Icon.setPiece((PieceType) piece1Combo.getSelectedItem(), true);
                fireConfigChanged();
            }
        });
        piece2Combo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                piece2Icon.setPiece((PieceType) piece2Combo.getSelectedItem(), false);
                fireConfigChanged();
            }
        });

        ChangeListener posChanged = e -> fireConfigChanged();
        piece1Row.addChangeListener(posChanged);
        piece1Col.addChangeListener(posChanged);
        piece2Row.addChangeListener(posChanged);
        piece2Col.addChangeListener(posChanged);

        // Initialise position spinner limits
        onBoardSizeChanged();
    }

    // =====================================================================
    //  Public API
    // =====================================================================

    /** @return Number of board rows. */
    public int getBoardRows() {
        return (int) rowsSpinner.getValue();
    }

    /** @return Number of board columns. */
    public int getBoardCols() {
        return (int) colsSpinner.getValue();
    }

    /** @return Selected type for piece 1. */
    public PieceType getPiece1Type() {
        return (PieceType) piece1Combo.getSelectedItem();
    }

    /** @return Selected type for piece 2. */
    public PieceType getPiece2Type() {
        return (PieceType) piece2Combo.getSelectedItem();
    }

    /** @return Starting row for piece 1. */
    public int getPiece1Row() {
        return (int) piece1Row.getValue();
    }

    /** @return Starting column for piece 1. */
    public int getPiece1Col() {
        return (int) piece1Col.getValue();
    }

    /** @return Starting row for piece 2. */
    public int getPiece2Row() {
        return (int) piece2Row.getValue();
    }

    /** @return Starting column for piece 2. */
    public int getPiece2Col() {
        return (int) piece2Col.getValue();
    }

    /**
     * Set the prediction text (Catalan formatted time string).
     *
     * @param text e.g. "< 1 segon", "5.3 segons"
     */
    public void setPredictionTime(String text) {
        predictionTimeLabel.setText(text);
    }

    /**
     * Set the difficulty text and colour.
     *
     * @param text  e.g. "Molt fàcil", "Difícil"
     * @param color Colour to indicate difficulty
     */
    public void setDifficulty(String text, Color color) {
        difficultyLabel.setText(text);
        difficultyLabel.setForeground(color);
    }

    /**
     * Register a callback invoked whenever any configuration value changes.
     *
     * @param listener Runnable to call on change
     */
    public void setOnConfigChanged(Runnable listener) {
        this.onConfigChanged = listener;
    }

    /**
     * Validate the current configuration.
     * Shows an error dialog if invalid.
     *
     * @return true if configuration is valid
     */
    public boolean validateConfig() {
        int rows = getBoardRows();
        int cols = getBoardCols();
        int r1 = getPiece1Row(), c1 = getPiece1Col();
        int r2 = getPiece2Row(), c2 = getPiece2Col();

        if (r1 >= rows || c1 >= cols) {
            showError("La posició de la Peça 1 (" + r1 + "," + c1 +
                      ") està fora del tauler " + rows + "x" + cols + ".");
            return false;
        }
        if (r2 >= rows || c2 >= cols) {
            showError("La posició de la Peça 2 (" + r2 + "," + c2 +
                      ") està fora del tauler " + rows + "x" + cols + ".");
            return false;
        }
        if (r1 == r2 && c1 == c2) {
            showError("Les dues peces no poden començar a la mateixa posició.");
            return false;
        }
        return true;
    }

    /**
     * Enable or disable all configuration controls.
     *
     * @param enabled true to enable, false to disable
     */
    public void setConfigEnabled(boolean enabled) {
        rowsSpinner.setEnabled(enabled);
        colsSpinner.setEnabled(enabled);
        piece1Combo.setEnabled(enabled);
        piece2Combo.setEnabled(enabled);
        piece1Row.setEnabled(enabled);
        piece1Col.setEnabled(enabled);
        piece2Row.setEnabled(enabled);
        piece2Col.setEnabled(enabled);
        place1Button.setEnabled(enabled);
        place2Button.setEnabled(enabled);
        if (!enabled) {
            clearPlacementMode();
        }
    }

    // =====================================================================
    //  Placement mode API
    // =====================================================================

    /**
     * Indicates which piece (if any) is in placement mode.
     *
     * @return 1 if placing piece 1, 2 if placing piece 2, 0 if no placement active
     */
    public int getActivePlacement() {
        if (place1Button.isSelected()) return 1;
        if (place2Button.isSelected()) return 2;
        return 0;
    }

    /**
     * Deactivate any active placement mode.
     */
    public void clearPlacementMode() {
        placementGroup.clearSelection();
    }

    /**
     * Programmatically set piece 1's starting position.
     * Updates the spinners and fires the config-changed callback.
     *
     * @param row Row index (0-based)
     * @param col Column index (0-based)
     */
    public void setPiece1Position(int row, int col) {
        piece1Row.setValue(row);
        piece1Col.setValue(col);
        // fireConfigChanged() will be triggered by spinner change listeners
    }

    /**
     * Programmatically set piece 2's starting position.
     * Updates the spinners and fires the config-changed callback.
     *
     * @param row Row index (0-based)
     * @param col Column index (0-based)
     */
    public void setPiece2Position(int row, int col) {
        piece2Row.setValue(row);
        piece2Col.setValue(col);
        // fireConfigChanged() will be triggered by spinner change listeners
    }

    // =====================================================================
    //  Internal helpers
    // =====================================================================

    /** Called when board rows/cols change: clamp position spinners. */
    private void onBoardSizeChanged() {
        int maxRow = getBoardRows() - 1;
        int maxCol = getBoardCols() - 1;

        updateSpinnerMax(piece1Row, maxRow);
        updateSpinnerMax(piece1Col, maxCol);
        updateSpinnerMax(piece2Row, maxRow);
        updateSpinnerMax(piece2Col, maxCol);

        fireConfigChanged();
    }

    private void updateSpinnerMax(JSpinner spinner, int max) {
        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
        model.setMaximum(max);
        if ((int) spinner.getValue() > max) {
            spinner.setValue(max);
        }
    }

    private void fireConfigChanged() {
        if (onConfigChanged != null) {
            onConfigChanged.run();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error de configuració",
                                      JOptionPane.ERROR_MESSAGE);
    }

    // =====================================================================
    //  Layout builder helpers
    // =====================================================================

    private JSpinner createBoundedSpinner(int min, int max, int value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setPreferredSize(new Dimension(60, 26));
        return spinner;
    }

    private int addSectionHeader(GridBagConstraints gbc, int row, String title) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
        add(label, gbc);
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        return row + 1;
    }

    private int addLabelledSpinner(GridBagConstraints gbc, int row, String text, JSpinner spinner) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        add(new JLabel(text), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        add(spinner, gbc);
        gbc.gridwidth = 1;
        return row + 1;
    }

    private int addLabelledComponent(GridBagConstraints gbc, int row, String text, JComponent comp) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        add(new JLabel(text), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        add(comp, gbc);
        gbc.gridwidth = 1;
        return row + 1;
    }

    private int addCentredComponent(GridBagConstraints gbc, int row, JComponent comp) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        add(comp, gbc);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 1;
        return row + 1;
    }

    private int addPositionRow(GridBagConstraints gbc, int row,
                               String text, JSpinner rowSpinner, JSpinner colSpinner) {
        // Label on its own full-width row
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        add(new JLabel(text), gbc);
        gbc.gridwidth = 1;
        row++;

        // Spinners on the next row: Fila: [spin]  Col: [spin]
        gbc.gridy = row;
        gbc.gridx = 0;
        add(new JLabel("Fila:"), gbc);
        gbc.gridx = 1;
        add(rowSpinner, gbc);

        gbc.gridx = 2;
        JPanel colPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        colPanel.add(new JLabel("Col:"));
        colPanel.add(colSpinner);
        add(colPanel, gbc);

        return row + 1;
    }

    private int addSeparator(GridBagConstraints gbc, int row) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(new JSeparator(), gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;
        return row + 1;
    }

    // =====================================================================
    //  Scrollable implementation — track viewport width, scroll vertically
    // =====================================================================

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return (orientation == SwingConstants.VERTICAL) ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        // Force panel width to match viewport — prevents horizontal overflow
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        // Allow vertical scrolling when content is taller than viewport
        return false;
    }
}
