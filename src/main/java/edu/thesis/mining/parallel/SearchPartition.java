// SearchPartition.java
package edu.thesis.mining.parallel;

import edu.thesis.mining.core.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Represents a partition of the search space assigned to a processor.
 */
public class SearchPartition {
    private final int id;
    private final Set<String> items;
    private final AtomicLong processedCount;
    private final AtomicBoolean shouldTerminate;
    private volatile double upperBound;

    public SearchPartition(int id, Set<String> items) {
        this.id = id;
        this.items = new HashSet<>(items);
        this.processedCount = new AtomicLong(0);
        this.shouldTerminate = new AtomicBoolean(false);
        this.upperBound = Double.POSITIVE_INFINITY;
    }

    public int getId() { return id; }
    public Set<String> getItems() { return new HashSet<>(items); }
    public long getProcessedCount() { return processedCount.get(); }
    public void incrementProcessedCount() { processedCount.incrementAndGet(); }
    public boolean shouldTerminate() { return shouldTerminate.get(); }
    public void signalTermination() { shouldTerminate.set(true); }
    public double getUpperBound() { return upperBound; }
    public void setUpperBound(double bound) { this.upperBound = bound; }

    /**
     * Transfer work to another partition for load balancing.
     */
    public void transferWorkTo(SearchPartition other) {
        // In a real implementation, this would transfer unexplored items
        // For now, we'll just log the action
        System.out.println("Transferring work from partition " + id + " to " + other.id);
    }

    /**
     * Get statistics for convergence monitoring.
     */
    public ConvergenceMonitor.ProcessorStats getStats() {
        ConvergenceMonitor.ProcessorStats stats = new ConvergenceMonitor.ProcessorStats();
        stats.totalProcessed = processedCount.get();
        // Additional stats would be tracked in production
        return stats;
    }
}
