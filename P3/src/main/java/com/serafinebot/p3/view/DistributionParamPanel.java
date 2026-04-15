package com.serafinebot.p3.view;

import com.serafinebot.p3.model.Distribution;
import com.serafinebot.p3.model.DistributionParams;
import com.serafinebot.p3.model.ParamSpec;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A panel that dynamically builds a labelled text field for each parameter
 * declared by the selected distribution. Adding, removing or renaming a
 * distribution's ParamSpec[] is automatically reflected here — the view has
 * no knowledge of which distributions exist or how many parameters each has.
 */
public class DistributionParamPanel extends JPanel {

    private final List<JTextField> fields = new ArrayList<>();
    private Distribution current;
    private Runnable onChange;

    public DistributionParamPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
    }

    public void setOnChange(Runnable r) {
        this.onChange = r;
    }

    /** Rebuilds the fields whenever the selected distribution changes. */
    public void setDistribution(Distribution dist) {
        if (dist == current) return;
        current = dist;

        removeAll();
        fields.clear();

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { if (onChange != null) onChange.run(); }
            public void removeUpdate(DocumentEvent e)  { if (onChange != null) onChange.run(); }
            public void changedUpdate(DocumentEvent e) { if (onChange != null) onChange.run(); }
        };

        for (ParamSpec spec : dist.params()) {
            add(new JLabel(spec.label() + ":"));
            JTextField tf = new JTextField(formatDefault(spec.defaultValue()), 5);
            tf.getDocument().addDocumentListener(dl);
            fields.add(tf);
            add(tf);
        }

        revalidate();
        repaint();
    }

    /**
     * Reads the current field values into a DistributionParams.
     * Falls back to the spec's default for any unparseable field.
     */
    public DistributionParams read() {
        if (current == null) return new DistributionParams(new double[0]);
        ParamSpec[] specs = current.params();
        double[] values = new double[specs.length];
        for (int i = 0; i < specs.length; i++) {
            values[i] = parseDouble(fields.get(i), specs[i].defaultValue());
        }
        return new DistributionParams(values);
    }

    private static String formatDefault(double v) {
        // Show up to 4 significant digits, strip trailing zeros
        return String.valueOf(Double.parseDouble(String.format("%.4g", v)));
    }

    private static double parseDouble(JTextField f, double fallback) {
        try {
            double v = Double.parseDouble(f.getText().trim());
            return Double.isFinite(v) ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
