// ConvergenceMonitor.java
package edu.thesis.mining.parallel;

import edu.thesis.mining.core.Itemset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors convergence of the mining process.
 * Implements Algorithm 6 from the paper.
 */
public class ConvergenceMonitor {
    private final int K;
    private final double epsilon;  // Approximation factor
    private final double requiredConfidence;  // Required confidence level

    // Tracking convergence metrics
    private final Map<Integer, List<Itemset>> topKHistory;
    private final AtomicInteger stableIterations;
    private final AtomicLong lastChangeTimestamp;

    // Processor statistics
    private final Map<Integer, ProcessorStats> processorStats;

    // Configuration
    private static final int STABILITY_THRESHOLD = 10;  // Iterations before considered stable
    private static final double WORK_EXHAUSTION_THRESHOLD = 0.01;

    public ConvergenceMonitor(int K, double epsilon, double requiredConfidence) {
        this.K = K;
        this.epsilon = epsilon;
        this.requiredConfidence = requiredConfidence;
        this.topKHistory = new ConcurrentHashMap<>();
        this.stableIterations = new AtomicInteger(0);
        this.lastChangeTimestamp = new AtomicLong(System.currentTimeMillis());
        this.processorStats = new ConcurrentHashMap<>();
    }

    /**
     * Check if the mining process has converged.
     * Uses multiple criteria to ensure quality results.
     */
    public boolean hasConverged(GlobalTopK globalTopK, List<SearchPartition> partitions) {
        // Gather progress statistics from all processors
        ProcessorStats aggregateStats = gatherProcessorStats(partitions);

        // Criterion 1: Check stability of top-K
        boolean stabilityReached = checkTopKStability(globalTopK);

        // Criterion 2: Check bound convergence
        boolean boundConverged = checkBoundConvergence(globalTopK, partitions);

        // Criterion 3: Check work exhaustion
        boolean workExhausted = checkWorkExhaustion(aggregateStats, partitions);

        // Criterion 4: Check probabilistic guarantee
        boolean confidenceMet = checkConfidenceLevel(globalTopK, aggregateStats);

        // Log convergence status
        if (shouldLogStatus()) {
            logConvergenceStatus(stabilityReached, boundConverged, workExhausted, confidenceMet);
        }

        // Converged if stability and bounds are met, OR work is exhausted, OR confidence is met
        return (stabilityReached && boundConverged) || workExhausted || confidenceMet;
    }

    /**
     * Check if top-K has remained unchanged for sufficient iterations.
     */
    private boolean checkTopKStability(GlobalTopK globalTopK) {
        List<Itemset> currentTopK = globalTopK.extractFinalTopK();
        int currentIteration = topKHistory.size();

        // Store current top-K
        topKHistory.put(currentIteration, new ArrayList<>(currentTopK));

        // Not enough history yet
        if (currentIteration < STABILITY_THRESHOLD) {
            return false;
        }

        // Check if top-K has changed in recent iterations
        boolean unchanged = true;
        List<Itemset> referenceTopK = topKHistory.get(currentIteration - STABILITY_THRESHOLD);

        if (referenceTopK == null || referenceTopK.size() != currentTopK.size()) {
            unchanged = false;
        } else {
            // Compare itemsets (order matters for top-K)
            for (int i = 0; i < currentTopK.size(); i++) {
                if (!currentTopK.get(i).equals(referenceTopK.get(i))) {
                    unchanged = false;
                    break;
                }
            }
        }

        if (unchanged) {
            stableIterations.incrementAndGet();
        } else {
            stableIterations.set(0);
            lastChangeTimestamp.set(System.currentTimeMillis());
        }

        return stableIterations.get() >= STABILITY_THRESHOLD;
    }

    /**
     * Check if global upper bound is close to K-th utility.
     */
    private boolean checkBoundConvergence(GlobalTopK globalTopK, List<SearchPartition> partitions) {
        double kthUtility = globalTopK.getKthUtility();

        // If we don't have K items yet, can't converge
        if (kthUtility == -Double.MAX_VALUE) {
            return false;
        }

        // Compute global upper bound across all unexplored space
        double globalUpperBound = computeGlobalUpperBound(partitions);

        // Check if upper bound is within epsilon of K-th utility
        return globalUpperBound < kthUtility * (1 + epsilon);
    }

    /**
     * Compute upper bound on unexplored search space.
     */
    private double computeGlobalUpperBound(List<SearchPartition> partitions) {
        double maxUpperBound = -Double.MAX_VALUE;

        for (SearchPartition partition : partitions) {
            double partitionBound = partition.getUpperBound();
            maxUpperBound = Math.max(maxUpperBound, partitionBound);
        }

        return maxUpperBound;
    }

    /**
     * Check if remaining work is negligible.
     */
    private boolean checkWorkExhaustion(ProcessorStats aggregateStats,
                                       List<SearchPartition> partitions) {
        // Estimate remaining work based on unexplored itemsets
        long totalPossibleItemsets = estimateTotalItemsets(partitions);
        long exploredItemsets = aggregateStats.totalProcessed;

        if (totalPossibleItemsets == 0) {
            return true;
        }

        double explorationRatio = (double) exploredItemsets / totalPossibleItemsets;

        // Also check if processing rate has dropped significantly
        double currentRate = aggregateStats.getProcessingRate();
        boolean lowProcessingRate = currentRate < 1.0;  // Less than 1 itemset per second

        return explorationRatio > (1 - WORK_EXHAUSTION_THRESHOLD) || lowProcessingRate;
    }

    /**
     * Estimate total possible itemsets in search space.
     */
    private long estimateTotalItemsets(List<SearchPartition> partitions) {
        long total = 0;

        for (SearchPartition partition : partitions) {
            int items = partition.getItems().size();
            // 2^n possible itemsets per partition (excluding empty set)
            total += (1L << items) - 1;
        }

        return total;
    }

    /**
     * Check if we've achieved required confidence level.
     */
    private boolean checkConfidenceLevel(GlobalTopK globalTopK, ProcessorStats aggregateStats) {
        // Calculate confidence based on multiple factors
        double stabilityScore = calculateStabilityScore();
        double coverageScore = calculateCoverageScore(aggregateStats);
        double boundTightness = calculateBoundTightness(globalTopK);

        // Weighted combination of scores
        double overallConfidence = 0.4 * stabilityScore +
                                 0.3 * coverageScore +
                                 0.3 * boundTightness;

        return overallConfidence >= requiredConfidence;
    }

    /**
     * Calculate stability score based on how long top-K has been stable.
     */
    private double calculateStabilityScore() {
        long timeSinceChange = System.currentTimeMillis() - lastChangeTimestamp.get();
        double minutesSinceChange = timeSinceChange / 60000.0;

        // Sigmoid function for smooth transition
        return 1.0 / (1.0 + Math.exp(-0.5 * (minutesSinceChange - 5)));
    }

    /**
     * Calculate coverage score based on exploration progress.
     */
    private double calculateCoverageScore(ProcessorStats stats) {
        // Based on diminishing returns in finding new high-utility itemsets
        double recentDiscoveryRate = stats.getRecentDiscoveryRate();

        // Low discovery rate indicates good coverage
        return 1.0 - Math.min(1.0, recentDiscoveryRate / 10.0);
    }

    /**
     * Calculate how tight the bounds are.
     */
    private double calculateBoundTightness(GlobalTopK globalTopK) {
        List<Itemset> topK = globalTopK.extractFinalTopK();
        if (topK.isEmpty()) {
            return 0.0;
        }

        // Compare utilities to their bounds
        double totalTightness = 0.0;

        for (Itemset itemset : topK) {
            double eu = itemset.getExpectedUtility();
            double ub = itemset.getUpperBound();

            if (ub > 0) {
                totalTightness += eu / ub;
            }
        }

        return totalTightness / topK.size();
    }

    /**
     * Gather statistics from all processors.
     */
    private ProcessorStats gatherProcessorStats(List<SearchPartition> partitions) {
        ProcessorStats aggregate = new ProcessorStats();

        for (SearchPartition partition : partitions) {
            ProcessorStats stats = partition.getStats();
            aggregate.merge(stats);
            processorStats.put(partition.getId(), stats);
        }

        return aggregate;
    }

    /**
     * Determine if we should log convergence status.
     */
    private boolean shouldLogStatus() {
        // Log every 10 seconds
        return System.currentTimeMillis() % 10000 < 100;
    }

    /**
     * Log current convergence status.
     */
    private void logConvergenceStatus(boolean stability, boolean bounds,
                                    boolean exhaustion, boolean confidence) {
        System.out.printf("Convergence Status - Stability: %b, Bounds: %b, " +
                         "Exhaustion: %b, Confidence: %b%n",
                         stability, bounds, exhaustion, confidence);
    }

    /**
     * Inner class for processor statistics.
     */
    static class ProcessorStats {
        long totalProcessed = 0;
        long recentDiscoveries = 0;
        long startTime = System.currentTimeMillis();
        long lastUpdateTime = System.currentTimeMillis();

        void merge(ProcessorStats other) {
            this.totalProcessed += other.totalProcessed;
            this.recentDiscoveries += other.recentDiscoveries;
        }

        double getProcessingRate() {
            long elapsed = System.currentTimeMillis() - startTime;
            return elapsed > 0 ? (double) totalProcessed / (elapsed / 1000.0) : 0.0;
        }

        double getRecentDiscoveryRate() {
            long elapsed = System.currentTimeMillis() - lastUpdateTime;
            return elapsed > 0 ? (double) recentDiscoveries / (elapsed / 1000.0) : 0.0;
        }
    }
}