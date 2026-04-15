package com.serafinebot.p3.model;

/**
 * Supported point-cloud distributions and their configurable parameters.
 */
public enum Distribution {
    UNIFORM("Uniforme"),
    GAUSSIAN("Gaussiana",
        new ParamSpec("Mitjana (0=min, 1=max)", 0.5),
        new ParamSpec("σ (fracció rang)",        1.0 / 6.0),
        new ParamSpec("Mult X",                  1.0),
        new ParamSpec("Mult Y",                  1.0)),
    EXPONENTIAL("Exponencial",
        new ParamSpec("Factor X (mitjana=rang/λx)", 5.0),
        new ParamSpec("Factor Y (mitjana=rang/λy)", 5.0)),
    CLUSTERED("Agrupada (Clusters)",
        new ParamSpec("Clusters", 5.0));

    private final String displayName;
    private final ParamSpec[] params;

    Distribution(String displayName, ParamSpec... params) {
        this.displayName = displayName;
        this.params = params;
    }

    /** Parameter specifications for this distribution (may be empty). */
    public ParamSpec[] params() { return params.clone(); }

    @Override
    public String toString() { return displayName; }
}
