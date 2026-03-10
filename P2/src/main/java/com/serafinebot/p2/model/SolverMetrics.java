package com.serafinebot.p2.model;

/**
 * Tracks statistics and metrics during the solving process.
 */
public class SolverMetrics {
    
    private long iterations;
    private long backtracks;
    private int maxDepth;
    private long startTime;
    private long endTime;
    private boolean solutionFound;
    
    /**
     * Create a new metrics tracker.
     */
    public SolverMetrics() {
        reset();
    }
    
    /**
     * Start the timer.
     */
    public void startTimer() {
        this.startTime = System.currentTimeMillis();
        this.endTime = 0;
    }
    
    /**
     * Stop the timer.
     */
    public void stopTimer() {
        this.endTime = System.currentTimeMillis();
    }
    
    /**
     * Get elapsed time in milliseconds.
     * If timer is still running, returns time since start.
     * @return Elapsed time in milliseconds
     */
    public long getElapsedTimeMillis() {
        if (startTime == 0) return 0;
        long end = (endTime == 0) ? System.currentTimeMillis() : endTime;
        return end - startTime;
    }
    
    /**
     * Get elapsed time in seconds.
     * @return Elapsed time in seconds
     */
    public double getElapsedTimeSeconds() {
        return getElapsedTimeMillis() / 1000.0;
    }
    
    /**
     * Increment the iteration counter.
     */
    public void incrementIterations() {
        this.iterations++;
    }
    
    /**
     * Increment the backtrack counter.
     */
    public void incrementBacktracks() {
        this.backtracks++;
    }
    
    /**
     * Update the maximum depth reached.
     * @param depth Current depth
     */
    public void updateMaxDepth(int depth) {
        if (depth > this.maxDepth) {
            this.maxDepth = depth;
        }
    }
    
    /**
     * Mark that a solution was found.
     */
    public void setSolutionFound(boolean found) {
        this.solutionFound = found;
    }
    
    /**
     * Reset all metrics to initial state.
     */
    public void reset() {
        this.iterations = 0;
        this.backtracks = 0;
        this.maxDepth = 0;
        this.startTime = 0;
        this.endTime = 0;
        this.solutionFound = false;
    }
    
    // Getters
    
    public long getIterations() {
        return iterations;
    }
    
    public long getBacktracks() {
        return backtracks;
    }
    
    public int getMaxDepth() {
        return maxDepth;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public boolean isSolutionFound() {
        return solutionFound;
    }
    
    @Override
    public String toString() {
        return String.format("SolverMetrics[iterations=%d, backtracks=%d, maxDepth=%d, time=%.2fs, found=%s]",
            iterations, backtracks, maxDepth, getElapsedTimeSeconds(), solutionFound);
    }
}
