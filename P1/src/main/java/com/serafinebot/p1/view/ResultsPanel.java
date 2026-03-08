package com.serafinebot.p1.view;

import com.serafinebot.p1.model.AlgorithmType;
import com.serafinebot.p1.model.Measurement;
import com.serafinebot.p1.model.Model;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Panell que mostra els resultats en forma de taula i
 * les constants multiplicatives i previsions en una area de text.
 */
public class ResultsPanel extends JPanel {

    private final Model model;
    private final DefaultTableModel tableModel;
    private final JTextArea infoArea;

    public ResultsPanel(Model model) {
        this.model = model;
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Resultats"));
        setPreferredSize(new Dimension(380, 0));

        // Taula de mesures
        String[] columns = {"Algorisme", "n", "Temps (ms)", "Constant c"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setFont(new Font("SansSerif", Font.PLAIN, 11));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane tableScroll = new JScrollPane(table);

        // Area d'informacio
        infoArea = new JTextArea(10, 30);
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane infoScroll = new JScrollPane(infoArea);
        infoScroll.setBorder(BorderFactory.createTitledBorder("Constants i Previsions"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, infoScroll);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);
    }

    /** Actualitza la taula i les constants amb les dades actuals del model. */
    public void updateResults() {
        tableModel.setRowCount(0);

        for (AlgorithmType type : AlgorithmType.values()) {
            List<Measurement> data = model.getMeasurements(type);
            for (Measurement m : data) {
                tableModel.addRow(new Object[]{
                        type.getDisplayName() + " " + type.getNotation(),
                        m.getN(),
                        String.format("%.4f", m.getTimeMs()),
                        String.format("%.4e", m.getConstant())
                });
            }
        }

        updateConstants();
    }

    private void updateConstants() {
        StringBuilder sb = new StringBuilder();
        sb.append("Constants multiplicatives mitjanes:\n");
        sb.append("  c = T(n) / f(n)\n\n");

        boolean anyData = false;
        for (AlgorithmType type : AlgorithmType.values()) {
            double c = model.getAverageConstant(type);
            if (!Double.isNaN(c)) {
                sb.append(String.format("  %-10s %s : c = %.4e\n",
                        type.getDisplayName(), type.getNotation(), c));
                anyData = true;
            }
        }
        if (!anyData) {
            sb.append("  (sense dades)\n");
        }
        infoArea.setText(sb.toString());
    }

    /** Mostra les previsions de temps per a un valor de n donat. */
    public void showPredictions(long predictN, Map<AlgorithmType, Double> predictions) {
        StringBuilder sb = new StringBuilder(infoArea.getText());
        sb.append(String.format("\n--- Previsions per n = %,d ---\n\n", predictN));

        for (AlgorithmType type : AlgorithmType.values()) {
            Double timeMs = predictions.get(type);
            if (timeMs != null && !timeMs.isNaN()) {
                sb.append(String.format("  %-10s : %s\n",
                        type.getDisplayName(), formatPrediction(timeMs)));
            } else {
                sb.append(String.format("  %-10s : (sense dades)\n",
                        type.getDisplayName()));
            }
        }

        infoArea.setText(sb.toString());
        infoArea.setCaretPosition(infoArea.getDocument().getLength());
    }

    private String formatPrediction(double ms) {
        if (ms >= 86_400_000) return String.format("%.2f dies (%.2e ms)", ms / 86_400_000, ms);
        if (ms >= 3_600_000) return String.format("%.2f hores", ms / 3_600_000);
        if (ms >= 60_000) return String.format("%.2f minuts", ms / 60_000);
        if (ms >= 1_000) return String.format("%.3f segons", ms / 1_000);
        return String.format("%.4f ms", ms);
    }
}
