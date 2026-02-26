package com.serafinebot.p1.view;

import com.serafinebot.p1.model.AlgorithmType;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Panell de controls. No exposa cap element visual a l'exterior.
 * Tota comunicacio amb el controlador es fa a traves de {@link ViewListener}.
 * La validacio d'entrada i els missatges d'error es gestionen internament.
 */
public class ControlPanel extends JPanel {

    private final JTextField startNField;
    private final JTextField endNField;
    private final JTextField stepsField;
    private final JTextField predictNField;
    private final Map<AlgorithmType, JButton> algorithmButtons = new EnumMap<>(AlgorithmType.class);
    private final Map<AlgorithmType, Boolean> runningState = new EnumMap<>(AlgorithmType.class);
    private final List<ViewListener> listeners = new ArrayList<>();

    public ControlPanel() {
        setLayout(new GridLayout(3, 1, 5, 2));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Controls"),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));

        for (AlgorithmType type : AlgorithmType.values()) {
            runningState.put(type, false);
        }

        // Fila 1: Rang de n
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.add(new JLabel("n inicial:"));
        startNField = new JTextField("100", 7);
        row1.add(startNField);
        row1.add(new JLabel("n final:"));
        endNField = new JTextField("5000", 7);
        row1.add(endNField);
        row1.add(new JLabel("Passos:"));
        stepsField = new JTextField("10", 4);
        row1.add(stepsField);
        JCheckBox logScaleCheck = new JCheckBox("Escala logaritmica");
        row1.add(Box.createHorizontalStrut(15));
        row1.add(logScaleCheck);
        add(row1);

        // Fila 2: Botons d'algorismes
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (AlgorithmType type : AlgorithmType.values()) {
            JButton btn = new JButton("\u25B6 " + type.getDisplayName());
            btn.setForeground(type.getColor());
            btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
            algorithmButtons.put(type, btn);
            row2.add(btn);

            btn.addActionListener(e -> {
                if (runningState.get(type)) {
                    for (ViewListener l : listeners) l.onStopAlgorithm(type);
                } else {
                    long[] nValues = parseNValues();
                    if (nValues != null) {
                        for (ViewListener l : listeners) l.onStartAlgorithm(type, nValues);
                    }
                }
            });
        }

        row2.add(new JSeparator(JSeparator.VERTICAL) {
            @Override public Dimension getPreferredSize() { return new Dimension(10, 25); }
        });

        JButton runAllButton = new JButton("\u25B6\u25B6 Tots");
        runAllButton.setFont(runAllButton.getFont().deriveFont(Font.BOLD));
        row2.add(runAllButton);
        runAllButton.addActionListener(e -> {
            long[] nValues = parseNValues();
            if (nValues != null) {
                for (ViewListener l : listeners) l.onStartAll(nValues);
            }
        });

        JButton stopAllButton = new JButton("\u25A0 Aturar Tots");
        stopAllButton.setFont(stopAllButton.getFont().deriveFont(Font.BOLD));
        row2.add(stopAllButton);
        stopAllButton.addActionListener(e -> {
            for (ViewListener l : listeners) l.onStopAll();
        });

        JButton clearButton = new JButton("Netejar");
        row2.add(clearButton);
        clearButton.addActionListener(e -> {
            for (ViewListener l : listeners) l.onClear();
        });

        add(row2);

        // Fila 3: Previsio
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row3.add(new JLabel("Previsio per n ="));
        predictNField = new JTextField("50000", 8);
        row3.add(predictNField);
        JButton predictButton = new JButton("Predir");
        row3.add(predictButton);
        predictButton.addActionListener(e -> {
            Long n = parsePredictN();
            if (n != null) {
                for (ViewListener l : listeners) l.onPredict(n);
            }
        });
        add(row3);

        // Escala logaritmica
        logScaleCheck.addActionListener(e -> {
            for (ViewListener l : listeners) l.onLogScaleChanged(logScaleCheck.isSelected());
        });
    }

    // ---- Parsing intern (no exposa res a l'exterior) ----

    private long[] parseNValues() {
        try {
            long start = Long.parseLong(startNField.getText().trim());
            long end = Long.parseLong(endNField.getText().trim());
            int steps = Integer.parseInt(stepsField.getText().trim());
            if (start <= 0 || end <= 0 || steps <= 0)
                throw new NumberFormatException("Els valors han de ser positius");
            if (steps == 1) return new long[]{start};
            long[] values = new long[steps];
            for (int i = 0; i < steps; i++) {
                values[i] = start + (end - start) * i / (steps - 1);
            }
            return values;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Valors d'entrada invalids: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private Long parsePredictN() {
        try {
            long n = Long.parseLong(predictNField.getText().trim());
            if (n <= 0) throw new NumberFormatException("n ha de ser positiu");
            return n;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Valor de n per a previsio invalid: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    // ---- API publica: nomes dades, mai elements visuals ----

    /** Actualitza l'estat visual d'un boto d'algorisme. */
    public void setAlgorithmRunning(AlgorithmType type, boolean running) {
        runningState.put(type, running);
        JButton btn = algorithmButtons.get(type);
        if (running) {
            btn.setText("\u25A0 " + type.getDisplayName());
            btn.setForeground(Color.RED);
        } else {
            btn.setText("\u25B6 " + type.getDisplayName());
            btn.setForeground(type.getColor());
        }
    }

    /** Registra un listener d'events de la vista. */
    public void addViewListener(ViewListener listener) {
        listeners.add(listener);
    }
}
