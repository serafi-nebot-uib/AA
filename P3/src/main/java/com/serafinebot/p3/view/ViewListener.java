package com.serafinebot.p3.view;

import com.serafinebot.p3.model.PointCloud;

/**
 * Events that the view can emit. The controller implements this interface
 * so the view never needs to hold a reference to any controller type.
 */
public interface ViewListener {
    void onGenerate(int n, PointCloud.Distribution distribution, double rangeMin, double rangeMax);
    void onRun();
    void onOpenBenchmark();
}
