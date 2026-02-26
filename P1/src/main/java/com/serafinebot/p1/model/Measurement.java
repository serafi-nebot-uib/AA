package com.serafinebot.p1.model;

/**
 * Classe que emmagatzema el resultat d'una mesura d'execució d'un algorisme.
 * Inclou el tipus d'algorisme, la mida n, el temps en nanosegons i la constant multiplicativa.
 */
public class Measurement {

    private final AlgorithmType type;
    private final long n;
    private final long timeNanos;
    private final double constant;

    public Measurement(AlgorithmType type, long n, long timeNanos) {
        this.type = type;
        this.n = n;
        this.timeNanos = timeNanos;
        double cost = type.theoreticalCost(n);
        this.constant = cost > 0 ? timeNanos / cost : 0;
    }

    public AlgorithmType getType() { return type; }
    public long getN() { return n; }
    public long getTimeNanos() { return timeNanos; }
    public double getConstant() { return constant; }

    /** Retorna el temps d'execució en mil·lisegons. */
    public double getTimeMs() {
        return timeNanos / 1_000_000.0;
    }
}
