package com.serafinebot.p3.model;

/**
 * Parameter values for a point-cloud distribution.
 * {@code get(i)} corresponds to {@code distribution.params()[i]}.
 */
public record DistributionParams(double[] values) {

    /** Defensive copy on construction so the record is immutable. */
    public DistributionParams {
        values = values.clone();
    }

    public double get(int i) { return values[i]; }

    /** Build a DistributionParams filled with each spec's default value. */
    public static DistributionParams defaults(PointCloud.Distribution dist) {
        ParamSpec[] specs = dist.params();
        double[] vals = new double[specs.length];
        for (int i = 0; i < specs.length; i++) vals[i] = specs[i].defaultValue();
        return new DistributionParams(vals);
    }
}
