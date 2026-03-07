package com.serafinebot.p1.model;

import java.util.*;

/**
 * Model central de l'aplicació MVC.
 * Emmagatzema les mesures de cada algorisme, calcula constants
 * multiplicatives mitjanes i realitza previsions de temps.
 */
public class Model {

    /** Interfície per notificar canvis al model. */
    public interface ModelListener {
        void modelChanged();
    }

    private final Map<AlgorithmType, List<Measurement>> measurements = new EnumMap<>(AlgorithmType.class);
    private final List<ModelListener> listeners = new ArrayList<>();

    public Model() {
        for (AlgorithmType type : AlgorithmType.values()) {
            measurements.put(type, new ArrayList<>());
        }
    }

    /** Afegeix una mesura i notifica als observadors. */
    public synchronized void addMeasurement(Measurement m) {
        measurements.get(m.getType()).add(m);
        measurements.get(m.getType()).sort(Comparator.comparingLong(Measurement::getN));
        fireModelChanged();
    }

    /** Retorna una còpia de les mesures d'un algorisme. */
    public synchronized List<Measurement> getMeasurements(AlgorithmType type) {
        return new ArrayList<>(measurements.get(type));
    }

    /** Calcula la constant multiplicativa mitjana: c = T(n) / f(n). */
    public synchronized double getAverageConstant(AlgorithmType type) {
        List<Measurement> list = measurements.get(type);
        if (list.isEmpty()) return Double.NaN;
        return list.stream()
                .mapToDouble(Measurement::getConstant)
                .average()
                .orElse(Double.NaN);
    }

    /**
     * Prediu el temps d'execució per a un valor de n donat.
     * @return temps predit en mil·lisegons, o NaN si no hi ha dades.
     */
    public synchronized double predict(AlgorithmType type, long n) {
        double c = getAverageConstant(type);
        if (Double.isNaN(c)) return Double.NaN;
        return c * type.theoreticalCost(n) / 1_000_000.0;
    }

    /** Esborra les mesures d'un algorisme concret. */
    public synchronized void clearMeasurements(AlgorithmType type) {
        measurements.get(type).clear();
        fireModelChanged();
    }

    /** Esborra totes les mesures de tots els algorismes. */
    public synchronized void clearAll() {
        for (AlgorithmType type : AlgorithmType.values()) {
            measurements.get(type).clear();
        }
        fireModelChanged();
    }

    /** Registra un observador de canvis al model. */
    public void addListener(ModelListener listener) {
        listeners.add(listener);
    }

    private void fireModelChanged() {
        for (ModelListener l : listeners) {
            l.modelChanged();
        }
    }
}
