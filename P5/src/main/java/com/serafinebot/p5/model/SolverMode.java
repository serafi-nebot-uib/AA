package com.serafinebot.p5.model;

/** Available dynamic-programming strategies for the same state recurrence. */
public enum SolverMode {
    /** Recursive memoized DP; computes only states reached from the query. */
    TOP_DOWN_DP,

    /** Iterative DP; computes the full table for the selected configuration. */
    BOTTOM_UP_DP
}
