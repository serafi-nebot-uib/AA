package com.serafinebot.p1.view;

import com.serafinebot.p1.model.AlgorithmType;

/**
 * Interficie que defineix els events que la vista pot emetre.
 * El controlador implementa aquesta interficie per rebre
 * notificacions sense accedir a cap element visual.
 */
public interface ViewListener {
    void onStartAlgorithm(AlgorithmType type, long[] nValues);
    void onStopAlgorithm(AlgorithmType type);
    void onStartAll(long[] nValues);
    void onStopAll();
    void onPredict(long predictN);
    void onClear();
    void onLogScaleChanged(boolean logScale);
}
