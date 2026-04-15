package com.serafinebot.p3.model;

/**
 * Application model (MVC). Holds the current point cloud, the results of each
 * algorithm run, and the benchmark series results.
 */
public class Model {

    private final PointCloud pointCloud = new PointCloud();

    private Point[] points;
    private Distribution currentDistribution;
    private DistributionParams currentParams;
    private PointPair closestBrute;
    private PointPair closestDC;
    private PointPair closestBucket;
    private PointPair farthest;
    private double timeBruteMs;
    private double timeDCMs;
    private double timeBucketMs;
    private double timeFarthestMs;
    private Benchmark.BenchmarkEntry[] benchmarkResults;

    public void generatePoints(int n, Distribution distribution,
                               double rangeMin, double rangeMax, DistributionParams params) {
        this.currentDistribution = distribution;
        this.currentParams       = params;
        this.points              = pointCloud.generate(n, distribution, rangeMin, rangeMax, params);
        this.closestBrute  = null;
        this.closestDC     = null;
        this.closestBucket = null;
        this.farthest      = null;
    }

    public void setResults(PointPair closestBrute,  double timeBruteMs,
                           PointPair closestDC,     double timeDCMs,
                           PointPair closestBucket, double timeBucketMs,
                           PointPair farthest,      double timeFarthestMs) {
        this.closestBrute   = closestBrute;
        this.timeBruteMs    = timeBruteMs;
        this.closestDC      = closestDC;
        this.timeDCMs       = timeDCMs;
        this.closestBucket  = closestBucket;
        this.timeBucketMs   = timeBucketMs;
        this.farthest       = farthest;
        this.timeFarthestMs = timeFarthestMs;
    }

    public void setBenchmarkResults(Benchmark.BenchmarkEntry[] results) {
        this.benchmarkResults = results;
    }

    public Point[]                     getPoints()              { return points; }
    public Distribution                getCurrentDistribution() { return currentDistribution; }
    public DistributionParams          getCurrentParams()       { return currentParams; }
    public PointPair getClosestBrute()                        { return closestBrute; }
    public PointPair getClosestDC()                           { return closestDC; }
    public PointPair getClosestBucket()                       { return closestBucket; }
    public PointPair getFarthest()                            { return farthest; }
    public double    getTimeBruteMs()                         { return timeBruteMs; }
    public double    getTimeDCMs()                            { return timeDCMs; }
    public double    getTimeBucketMs()                        { return timeBucketMs; }
    public double    getTimeFarthestMs()                      { return timeFarthestMs; }
    public Benchmark.BenchmarkEntry[] getBenchmarkResults()   { return benchmarkResults; }
}
