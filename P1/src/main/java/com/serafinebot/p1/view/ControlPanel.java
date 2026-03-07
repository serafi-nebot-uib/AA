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

    public interface VisibilityListener {
        void onVisibilityChanged(AlgorithmType type, boolean visible);
    }

    private final JTextField endNField;
    private final JTextField stepsField;
    private final JTextField predictNField;
    private final Map<AlgorithmType, JButton> algorithmButtons = new EnumMap<>(AlgorithmType.class);
    private final Map<AlgorithmType, Boolean> runningState = new EnumMap<>(AlgorithmType.class);
    private final List<ViewListener> listeners = new ArrayList<>();
    private final List<VisibilityListener> visibilityListeners = new ArrayList<>();

    public ControlPanel() {
        setLayout(new GridLayout(4, 1, 5, 2));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Controls"),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));

        for (AlgorithmType type : AlgorithmType.values()) {
            runningState.put(type, false);
        }

        // Fila 1: Rang de n
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.add(new JLabel("n final:"));
        endNField = new JTextField("5000", 7);
        row1.add(endNField);
        row1.add(new JLabel("Mostres:"));
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

        // Fila 3: Visibilitat d'algorismes a la grafica
        JPanel row3vis = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row3vis.add(new JLabel("Mostrar:"));
        for (AlgorithmType type : AlgorithmType.values()) {
            JCheckBox cb = new JCheckBox(type.getDisplayName(), true);
            cb.setForeground(type.getColor());
            cb.setFont(cb.getFont().deriveFont(Font.BOLD, 11f));
            cb.addActionListener(e -> {
                for (VisibilityListener l : visibilityListeners)
                    l.onVisibilityChanged(type, cb.isSelected());
            });
            row3vis.add(cb);
        }
        add(row3vis);

        // Fila 4: Previsio
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
            long end = Long.parseLong(endNField.getText().trim());
            int numSamples = Integer.parseInt(stepsField.getText().trim());
            if (end <= 0 || numSamples <= 0)
                throw new NumberFormatException("Els valors han de ser positius");

            long[] values = new long[numSamples];
            for (int i = 0; i < numSamples; i++) {
                values[i] = end * (i + 1) / numSamples;
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

    /** Registra un listener de canvis de visibilitat d'algorismes. */
    public void addVisibilityListener(VisibilityListener listener) {
        visibilityListeners.add(listener);
    }
}
