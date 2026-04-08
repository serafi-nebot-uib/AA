package com.serafinebot.p3.model;

public class Model {

    private final PointCloud pointCloud = new PointCloud();

    private Point[] points;
    private PointPair closestBrute;
    private PointPair closestDC;
    private PointPair farthest;
    private double timeBruteMs;
    private double timeDCMs;
    private double timeFarthestMs;
    private Benchmark.BenchmarkEntry[] benchmarkResults;

    public void generatePoints(int n, PointCloud.Distribution distribution, double rangeMin, double rangeMax) {
        this.points = pointCloud.generate(n, distribution, rangeMin, rangeMax);
        this.closestBrute = null;
        this.closestDC = null;
        this.farthest = null;
    }

    public void setResults(PointPair closestBrute, double timeBruteMs,
                           PointPair closestDC,    double timeDCMs,
                           PointPair farthest,     double timeFarthestMs) {
        this.closestBrute   = closestBrute;
        this.timeBruteMs    = timeBruteMs;
        this.closestDC      = closestDC;
        this.timeDCMs       = timeDCMs;
        this.farthest       = farthest;
        this.timeFarthestMs = timeFarthestMs;
    }

    public void setBenchmarkResults(Benchmark.BenchmarkEntry[] results) {
        this.benchmarkResults = results;
    }

    public Point[] getPoints()                              { return points; }
    public PointPair getClosestBrute()                      { return closestBrute; }
    public PointPair getClosestDC()                         { return closestDC; }
    public PointPair getFarthest()                          { return farthest; }
    public double getTimeBruteMs()                          { return timeBruteMs; }
    public double getTimeDCMs()                             { return timeDCMs; }
    public double getTimeFarthestMs()                       { return timeFarthestMs; }
    public Benchmark.BenchmarkEntry[] getBenchmarkResults() { return benchmarkResults; }
}
