// ProcessorTask.java
package edu.thesis.mining.parallel;

import edu.thesis.mining.algorithms.*;
import edu.thesis.mining.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Task executed by each processor in the parallel mining framework.
 */
public class ProcessorTask implements Callable<List<Itemset>> {
    private final int processorId;
    private final SearchPartition partition;
    private final Database localDB;
    private final ProbabilisticUtilityTensor PUT;
    private final GlobalTopK globalTopK;
    private final AtomicReference<Double> globalThreshold;

    // Local candidates
    private final List<Itemset> localCandidates;

    // Synchronization control
    private int iterationCount = 0;
    private static final int SYNC_INTERVAL = 1000;

    public ProcessorTask(int processorId,
                        SearchPartition partition,
                        Database localDB,
                        ProbabilisticUtilityTensor PUT,
                        GlobalTopK globalTopK,
                        AtomicReference<Double> globalThreshold) {
        this.processorId = processorId;
        this.partition = partition;
        this.localDB = localDB;
        this.PUT = PUT;
        this.globalTopK = globalTopK;
        this.globalThreshold = globalThreshold;
        this.localCandidates = new ArrayList<>();
    }

    @Override
    public List<Itemset> call() throws Exception {
        System.out.println("Processor " + processorId + " starting with " +
                          partition.getItems().size() + " items");

        // Main mining loop
        while (!partition.shouldTerminate()) {
            // Create mining instance for this iteration
            UncertainMine miner = new UncertainMine(partition, PUT, globalThreshold, localDB);

            // Mine candidates from partition
            List<Itemset> newCandidates = miner.mine();
            localCandidates.addAll(newCandidates);

            // Periodic synchronization
            if (shouldSync()) {
                synchronizeWithGlobal();
            }

            iterationCount++;

            // Check for termination or completion
            if (partition.getProcessedCount() >= estimateMaxItemsets()) {
                break;
            }
        }

        // Final synchronization
        synchronizeWithGlobal();

        System.out.println("Processor " + processorId + " completed. Found " +
                          localCandidates.size() + " candidates");

        return localCandidates;
    }

    /**
     * Check if synchronization is needed.
     */
    private boolean shouldSync() {
        return iterationCount % SYNC_INTERVAL == 0 ||
               localCandidates.size() > 100;
    }

    /**
     * Synchronize local candidates with global top-K.
     */
    private void synchronizeWithGlobal() {
        if (!localCandidates.isEmpty()) {
            // Update global top-K
            globalTopK.updateWithCandidates(localCandidates);

            // Get new threshold
            double newThreshold = globalTopK.getMinUtility();
            globalThreshold.set(newThreshold);

            // Clear processed local candidates
            localCandidates.clear();
        }
    }

    /**
     * Estimate maximum possible itemsets in partition.
     */
    private long estimateMaxItemsets() {
        int n = partition.getItems().size();
        // Limit to reasonable size to avoid exponential explosion
        return Math.min((1L << n) - 1, 1_000_000L);
    }
}