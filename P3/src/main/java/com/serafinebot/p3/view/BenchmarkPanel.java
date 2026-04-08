package com.serafinebot.p3.view;

import com.serafinebot.p3.model.Benchmark;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class BenchmarkPanel extends JPanel {

    private final DefaultTableModel tableModel;
    private final JTable table;

    public BenchmarkPanel() {
        setLayout(new BorderLayout());

        String[] columns = {"N", "Bruta O(n\u00B2) ms", "D&C O(n\u00B7log n) ms", "Mes lluny ms", "Speedup"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void setResults(Benchmark.BenchmarkEntry[] entries) {
        tableModel.setRowCount(0);
        if (entries == null) return;

        for (Benchmark.BenchmarkEntry entry : entries) {
            double brute = entry.bruteForce().averageTimeMs();
            double dc = entry.divideConquer().averageTimeMs();
            double farthest = entry.farthestPair().averageTimeMs();
            double speedup = dc > 0 ? brute / dc : 0;

            tableModel.addRow(new Object[]{
                entry.n(),
                String.format("%.4f", brute),
                String.format("%.4f", dc),
                String.format("%.4f", farthest),
                String.format("%.2fx", speedup)
            });
        }
    }
}
