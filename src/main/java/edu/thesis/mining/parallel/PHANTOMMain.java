// PHANTOMMain.java
package edu.thesis.mining.parallel;

import edu.thesis.mining.core.*;
import edu.thesis.mining.algorithms.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main parallel framework for mining top-K high-utility itemsets
 * from uncertain databases with mixed utilities.
 * PHANTOM: Parallel High-utility Algorithm for Negative and posiTive utilities On Mixed-uncertain data
 */
public class PHANTOMMain {
    private final Database database;
    private final int K;  // Number of desired itemsets
    private final int numProcessors;
    private final ExecutorService executorService;

    // Global shared structures
    private final GlobalTopK globalTopK;
    private final AtomicReference<Double> globalThreshold;
    private ProbabilisticUtilityTensor PUT;

    // Convergence monitoring
    private final ConvergenceMonitor convergenceMonitor;

    // Configuration parameters
    private static final double EPSILON = 0.01;  // Approximation factor
    private static final double CONFIDENCE = 0.95;  // Required confidence level
    private static final int SYNC_INTERVAL = 1000;  // Synchronization frequency

    public PHANTOMMain(Database database, int K, int numProcessors) {
        this.database = database;
        this.K = K;
        this.numProcessors = numProcessors;
        this.executorService = Executors.newFixedThreadPool(numProcessors);
        this.globalTopK = new GlobalTopK(K);
        this.globalThreshold = new AtomicReference<>(-Double.MAX_VALUE);
        this.convergenceMonitor = new ConvergenceMonitor(K, EPSILON, CONFIDENCE);
    }

    /**
     * Main entry point for parallel mining.
     * Implements Algorithm 1 from the paper.
     */
    public List<Itemset> mine() throws InterruptedException, ExecutionException {
        System.out.println("Starting PHANTOM mining with " + numProcessors + " processors...");

        // Phase 1: Initialize global structures and analyze database
        System.out.println("Phase 1: Initializing and analyzing database...");
        initializeGlobalStructures();

        // Phase 2: Partition the search space
        System.out.println("Phase 2: Partitioning search space...");
        List<SearchPartition> partitions = partitionSearchSpace();

        // Phase 3: Parallel mining with convergence monitoring
        System.out.println("Phase 3: Starting parallel mining...");
        List<Future<List<Itemset>>> futures = new ArrayList<>();

        // Create processor tasks
        for (int i = 0; i < numProcessors; i++) {
            ProcessorTask task = new ProcessorTask(
                i,
                partitions.get(i),
                database.getPartition(i, numProcessors),
                PUT,
                globalTopK,
                globalThreshold
            );
            futures.add(executorService.submit(task));
        }

        // Monitor convergence
        while (!convergenceMonitor.hasConverged(globalTopK, partitions)) {
            Thread.sleep(100);  // Check every 100ms

            // Dynamic load balancing if needed
            if (isLoadImbalanced(partitions)) {
                redistributeWork(partitions);
            }
        }

        // Signal termination to all processors
        for (SearchPartition partition : partitions) {
            partition.signalTermination();
        }

        // Wait for all processors to complete
        for (Future<List<Itemset>> future : futures) {
            future.get();
        }

        executorService.shutdown();

        // Extract final top-K
        return globalTopK.extractFinalTopK();
    }

    /**
     * Initialize global data structures including the Probabilistic Utility Tensor.
     */
    private void initializeGlobalStructures() {
        // Construct the Probabilistic Utility Tensor for efficient EU computation
        this.PUT = new ProbabilisticUtilityTensor(database);

        // Analyze uncertainty characteristics
        UncertaintyProfile profile = analyzeUncertainty();

        // Compute utility statistics for intelligent partitioning
        UtilityStats stats = computeUtilityStatistics();

        System.out.println("Database statistics:");
        System.out.println("  Total transactions: " + database.size());
        System.out.println("  Total items: " + database.getItemCount());
        System.out.println("  Average uncertainty: " + profile.getAverageUncertainty());
        System.out.println("  Negative utility ratio: " + stats.getNegativeRatio());
    }

    /**
     * Analyze uncertainty characteristics of the database.
     */
    private UncertaintyProfile analyzeUncertainty() {
        double totalExistenceProb = 0.0;
        double totalItemProb = 0.0;
        int itemCount = 0;

        for (Transaction t : database.getTransactions()) {
            totalExistenceProb += t.getExistenceProbability();

            for (String item : t.getItems()) {
                totalItemProb += t.getItemProbability(item);
                itemCount++;
            }
        }

        double avgExistence = totalExistenceProb / database.size();
        double avgItemProb = itemCount > 0 ? totalItemProb / itemCount : 0.0;

        return new UncertaintyProfile(avgExistence, avgItemProb);
    }

    /**
     * Compute utility statistics for partitioning.
     */
    private UtilityStats computeUtilityStatistics() {
        int positiveCount = 0;
        int negativeCount = 0;
        double totalPositive = 0.0;
        double totalNegative = 0.0;

        Set<String> allItems = database.getAllItems();

        for (String item : allItems) {
            double eu = PUT.getSingleItemEU(item);
            if (eu > 0) {
                positiveCount++;
                totalPositive += eu;
            } else if (eu < 0) {
                negativeCount++;
                totalNegative += Math.abs(eu);
            }
        }

        return new UtilityStats(positiveCount, negativeCount, totalPositive, totalNegative);
    }

    /**
     * Partition the search space among processors.
     * Uses utility statistics for intelligent load balancing.
     */
    private List<SearchPartition> partitionSearchSpace() {
        List<SearchPartition> partitions = new ArrayList<>();
        Set<String> allItems = database.getAllItems();
        List<String> sortedItems = new ArrayList<>(allItems);

        // Sort items by expected utility for better partitioning
        sortedItems.sort((a, b) ->
            Double.compare(PUT.getSingleItemEU(b), PUT.getSingleItemEU(a))
        );

        // Create balanced partitions
        int itemsPerPartition = (sortedItems.size() + numProcessors - 1) / numProcessors;

        for (int i = 0; i < numProcessors; i++) {
            int start = i * itemsPerPartition;
            int end = Math.min(start + itemsPerPartition, sortedItems.size());

            if (start < sortedItems.size()) {
                Set<String> partitionItems = new HashSet<>(
                    sortedItems.subList(start, end)
                );
                partitions.add(new SearchPartition(i, partitionItems));
            }
        }

        return partitions;
    }

    /**
     * Check if load is imbalanced across processors.
     */
    private boolean isLoadImbalanced(List<SearchPartition> partitions) {
        double[] workloads = new double[partitions.size()];
        double totalWork = 0.0;

        for (int i = 0; i < partitions.size(); i++) {
            workloads[i] = partitions.get(i).getProcessedCount();
            totalWork += workloads[i];
        }

        double avgWork = totalWork / partitions.size();
        double maxDeviation = 0.0;

        for (double work : workloads) {
            maxDeviation = Math.max(maxDeviation, Math.abs(work - avgWork));
        }

        // Consider imbalanced if deviation > 20% of average
        return maxDeviation > 0.2 * avgWork;
    }

    /**
     * Redistribute work among processors to balance load.
     */
    private void redistributeWork(List<SearchPartition> partitions) {
        // Find overloaded and underloaded partitions
        double avgWork = partitions.stream()
            .mapToDouble(SearchPartition::getProcessedCount)
            .average()
            .orElse(0.0);

        List<SearchPartition> overloaded = new ArrayList<>();
        List<SearchPartition> underloaded = new ArrayList<>();

        for (SearchPartition partition : partitions) {
            double work = partition.getProcessedCount();
            if (work > avgWork * 1.2) {
                overloaded.add(partition);
            } else if (work < avgWork * 0.8) {
                underloaded.add(partition);
            }
        }

        // Transfer work from overloaded to underloaded
        for (int i = 0; i < Math.min(overloaded.size(), underloaded.size()); i++) {
            SearchPartition from = overloaded.get(i);
            SearchPartition to = underloaded.get(i);
            from.transferWorkTo(to);
        }
    }
}