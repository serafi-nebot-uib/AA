package com.serafinebot.p2.view;

import com.serafinebot.p2.model.PieceType;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * Configuration panel for the Hamiltonian path solver.
 * Allows the user to set board dimensions, piece types and initial positions.
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

    // ---- White piece controls ----
    private final JComboBox<PieceType> whiteCombo;
    private final PieceIcon whiteIcon;
    private final JSpinner whiteRow;
    private final JSpinner whiteCol;

    // ---- Black piece controls ----
    private final JComboBox<PieceType> blackCombo;
    private final PieceIcon blackIcon;
    private final JSpinner blackRow;
    private final JSpinner blackCol;

    // ---- Placement mode buttons ----
    private final JToggleButton place1Button;
    private final JToggleButton place2Button;
    private final ButtonGroup placementGroup;

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

        whiteCombo = new JComboBox<>(PieceType.values());
        whiteCombo.setToolTipText("Selecciona el tipus de peça blanca");
        whiteIcon = new PieceIcon(PieceType.KNIGHT, true);

        row = addLabelledComponent(gbc, row, "Tipus:", whiteCombo);
        row = addCentredComponent(gbc, row, whiteIcon);

        whiteRow = createBoundedSpinner(1, 12, 1);
        whiteCol = createBoundedSpinner(1, 12, 1);
        row = addPositionRow(gbc, row, "Posició inicial:", whiteRow, whiteCol);

        place1Button = new JToggleButton("\u2295 Col\u00b7locar al tauler");
        place1Button.setToolTipText("Fes clic a una casella del tauler per situar la peça blanca");
        place1Button.setFocusPainted(false);
        row = addCentredComponent(gbc, row, place1Button);
        row = addSeparator(gbc, row);

        // =====================================================================
        // Piece 2 section
        // =====================================================================
        row = addSectionHeader(gbc, row, "Peça 2 (negra)");

        blackCombo = new JComboBox<>(PieceType.values());
        blackCombo.setToolTipText("Selecciona el tipus de peça negra");
        blackIcon = new PieceIcon(PieceType.KNIGHT, false);

        row = addLabelledComponent(gbc, row, "Tipus:", blackCombo);
        row = addCentredComponent(gbc, row, blackIcon);

        blackRow = createBoundedSpinner(1, 12, 1);
        blackCol = createBoundedSpinner(1, 12, 2);
        row = addPositionRow(gbc, row, "Posició inicial:", blackRow, blackCol);

        place2Button = new JToggleButton("\u2295 Col\u00b7locar al tauler");
        place2Button.setToolTipText("Fes clic a una casella del tauler per situar la peça negra");
        place2Button.setFocusPainted(false);
        row = addCentredComponent(gbc, row, place2Button);
        row = addSeparator(gbc, row);

        // Only one placement mode at a time (mutual exclusion)
        placementGroup = new ButtonGroup();
        placementGroup.add(place1Button);
        placementGroup.add(place2Button);

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

        whiteCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                whiteIcon.setPiece((PieceType) whiteCombo.getSelectedItem(), true);
                fireConfigChanged();
            }
        });
        blackCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                blackIcon.setPiece((PieceType) blackCombo.getSelectedItem(), false);
                fireConfigChanged();
            }
        });

        ChangeListener posChanged = e -> fireConfigChanged();
        whiteRow.addChangeListener(posChanged);
        whiteCol.addChangeListener(posChanged);
        blackRow.addChangeListener(posChanged);
        blackCol.addChangeListener(posChanged);

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

    /** @return Selected type for the white piece. */
    public PieceType getWhiteType() {
        return (PieceType) whiteCombo.getSelectedItem();
    }

    /** @return Selected type for the black piece. */
    public PieceType getBlackType() {
        return (PieceType) blackCombo.getSelectedItem();
    }

    /** @return Starting row for the white piece (0-based, for the model). */
    public int getWhiteRow() {
        return (int) whiteRow.getValue() - 1;
    }

    /** @return Starting column for the white piece (0-based, for the model). */
    public int getWhiteCol() {
        return (int) whiteCol.getValue() - 1;
    }

    /** @return Starting row for the black piece (0-based, for the model). */
    public int getBlackRow() {
        return (int) blackRow.getValue() - 1;
    }

    /** @return Starting column for the black piece (0-based, for the model). */
    public int getBlackCol() {
        return (int) blackCol.getValue() - 1;
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
        int wr = getWhiteRow(), wc = getWhiteCol();
        int br = getBlackRow(), bc = getBlackCol();

        if (wr >= rows || wc >= cols) {
            showError("La posició de la peça blanca (" + (wr + 1) + "," + (wc + 1) +
                      ") està fora del tauler " + rows + "x" + cols + ".");
            return false;
        }
        if (br >= rows || bc >= cols) {
            showError("La posició de la peça negra (" + (br + 1) + "," + (bc + 1) +
                      ") està fora del tauler " + rows + "x" + cols + ".");
            return false;
        }
        if (wr == br && wc == bc) {
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
        whiteCombo.setEnabled(enabled);
        blackCombo.setEnabled(enabled);
        whiteRow.setEnabled(enabled);
        whiteCol.setEnabled(enabled);
        blackRow.setEnabled(enabled);
        blackCol.setEnabled(enabled);
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
     * Programmatically set the white piece's starting position.
     * Updates the spinners and fires the config-changed callback.
     *
     * @param row Row index (0-based, from the model/board click)
     * @param col Column index (0-based, from the model/board click)
     */
    public void setWhitePosition(int row, int col) {
        whiteRow.setValue(row + 1);
        whiteCol.setValue(col + 1);
        // fireConfigChanged() will be triggered by spinner change listeners
    }

    /**
     * Programmatically set the black piece's starting position.
     * Updates the spinners and fires the config-changed callback.
     *
     * @param row Row index (0-based, from the model/board click)
     * @param col Column index (0-based, from the model/board click)
     */
    public void setBlackPosition(int row, int col) {
        blackRow.setValue(row + 1);
        blackCol.setValue(col + 1);
        // fireConfigChanged() will be triggered by spinner change listeners
    }

    // =====================================================================
    //  Internal helpers
    // =====================================================================

    /** Called when board rows/cols change: clamp position spinners. */
    private void onBoardSizeChanged() {
        int maxRow = getBoardRows();
        int maxCol = getBoardCols();

        updateSpinnerMax(whiteRow, maxRow);
        updateSpinnerMax(whiteCol, maxCol);
        updateSpinnerMax(blackRow, maxRow);
        updateSpinnerMax(blackCol, maxCol);

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
        // Label on its own full-width x
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        add(new JLabel(text), gbc);
        gbc.gridwidth = 1;
        row++;

        // Spinners on the next x: Fila: [spin]  Col: [spin]
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
