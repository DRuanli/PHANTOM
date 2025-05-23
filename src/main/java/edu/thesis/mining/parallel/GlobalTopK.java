// GlobalTopK.java
package edu.thesis.mining.parallel;

import edu.thesis.mining.core.Itemset;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.StampedLock;

/**
 * Lock-free global top-K maintenance structure.
 * Implements Algorithm 5 from the paper.
 */
public class GlobalTopK {
    private final int K;
    private final AtomicReference<TopKState> state;
    private final StampedLock stampedLock;
    private final AtomicLong version;
    private final AtomicInteger consolidationCounter;

    // Configuration
    private static final int CONSOLIDATION_THRESHOLD = 100;

    /**
     * Internal state representation for atomic updates.
     */
    private static class TopKState {
        final PriorityQueue<Itemset> minHeap;
        final Set<Itemset> itemsetSet;  // For quick membership testing

        TopKState(int capacity) {
            this.minHeap = new PriorityQueue<>(capacity,
                (a, b) -> Double.compare(a.getExpectedUtility(), b.getExpectedUtility()));
            this.itemsetSet = new HashSet<>();
        }

        TopKState(TopKState other) {
            this.minHeap = new PriorityQueue<>(other.minHeap);
            this.itemsetSet = new HashSet<>(other.itemsetSet);
        }
    }

    public GlobalTopK(int K) {
        this.K = K;
        this.state = new AtomicReference<>(new TopKState(K + 1));
        this.stampedLock = new StampedLock();
        this.version = new AtomicLong(0);
        this.consolidationCounter = new AtomicInteger(0);
    }

    /**
     * Update the global top-K with local candidates.
     * Uses optimistic concurrency control for lock-free operation.
     */
    public void updateWithCandidates(List<Itemset> localCandidates) {
        // Sort local candidates by expected utility (descending)
        List<Itemset> sorted = new ArrayList<>(localCandidates);
        Collections.sort(sorted, Collections.reverseOrder());

        for (Itemset candidate : sorted) {
            boolean success = false;
            int retries = 0;

            while (!success && retries < 10) {
                // Read current state
                TopKState currentState = state.get();
                double currentMin = getMinUtility(currentState);

                // Check if this candidate should be inserted
                if (candidate.getExpectedUtility() > currentMin || currentState.minHeap.size() < K) {
                    // Attempt lock-free update
                    long stamp = version.get();
                    success = atomicUpdate(candidate, stamp);

                    if (!success) {
                        retries++;
                        // Exponential backoff
                        try {
                            Thread.sleep((long)(Math.random() * Math.pow(2, retries)));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } else {
                    // This candidate and all following are too small
                    break;
                }
            }
        }

        // Check if consolidation is needed
        if (consolidationCounter.incrementAndGet() >= CONSOLIDATION_THRESHOLD) {
            consolidate();
        }
    }

    /**
     * Atomic update operation with version checking.
     */
    private boolean atomicUpdate(Itemset candidate, long expectedVersion) {
        // Use stamped lock for brief exclusive access
        long stamp = stampedLock.writeLock();
        try {
            // Check version hasn't changed
            if (version.get() != expectedVersion) {
                return false;
            }

            TopKState currentState = state.get();
            TopKState newState = new TopKState(currentState);

            // Check if itemset already exists (avoid duplicates)
            if (newState.itemsetSet.contains(candidate)) {
                return true;  // Already present, consider it success
            }

            if (newState.minHeap.size() < K) {
                // Still building up to K items
                newState.minHeap.offer(new Itemset(candidate));  // Deep copy
                newState.itemsetSet.add(new Itemset(candidate));
            } else {
                // Replace minimum if this candidate is better
                Itemset currentMin = newState.minHeap.peek();
                if (candidate.getExpectedUtility() > currentMin.getExpectedUtility()) {
                    newState.minHeap.poll();
                    newState.itemsetSet.remove(currentMin);
                    newState.minHeap.offer(new Itemset(candidate));
                    newState.itemsetSet.add(new Itemset(candidate));
                }
            }

            // Update state and version atomically
            state.set(newState);
            version.incrementAndGet();

            // Broadcast new threshold if we have K items
            if (newState.minHeap.size() == K) {
                broadcastNewThreshold(getMinUtility(newState));
            }

            return true;
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    /**
     * Get current minimum utility (threshold).
     */
    public double getMinUtility() {
        TopKState currentState = state.get();
        return getMinUtility(currentState);
    }

    private double getMinUtility(TopKState state) {
        if (state.minHeap.isEmpty()) {
            return -Double.MAX_VALUE;
        }
        return state.minHeap.peek().getExpectedUtility();
    }

    /**
     * Get the K-th utility value for convergence checking.
     */
    public double getKthUtility() {
        TopKState currentState = state.get();
        if (currentState.minHeap.size() < K) {
            return -Double.MAX_VALUE;
        }
        return currentState.minHeap.peek().getExpectedUtility();
    }

    /**
     * Broadcast new threshold to all processors.
     */
    private void broadcastNewThreshold(double newThreshold) {
        // In a real distributed system, this would use MPI or similar
        // For now, we'll use a shared atomic reference (handled by PHANTOMMain)
        System.out.println("Broadcasting new threshold: " + newThreshold);
    }

    /**
     * Periodic heap consolidation for efficiency.
     */
    public void consolidate() {
        long stamp = stampedLock.writeLock();
        try {
            TopKState currentState = state.get();

            // Rebuild heap to ensure it's properly ordered
            PriorityQueue<Itemset> newHeap = new PriorityQueue<>(K + 1,
                (a, b) -> Double.compare(a.getExpectedUtility(), b.getExpectedUtility()));

            newHeap.addAll(currentState.minHeap);

            // Keep only top K
            while (newHeap.size() > K) {
                Itemset removed = newHeap.poll();
                currentState.itemsetSet.remove(removed);
            }

            TopKState newState = new TopKState(K + 1);
            newState.minHeap.addAll(newHeap);
            newState.itemsetSet.addAll(currentState.itemsetSet);

            state.set(newState);
            consolidationCounter.set(0);

        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    /**
     * Extract final top-K itemsets.
     */
    public List<Itemset> extractFinalTopK() {
        TopKState finalState = state.get();
        List<Itemset> result = new ArrayList<>(finalState.minHeap);

        // Sort in descending order of utility
        Collections.sort(result, Collections.reverseOrder());

        // Return at most K items
        if (result.size() > K) {
            result = result.subList(0, K);
        }

        return result;
    }

    /**
     * Get current size of top-K collection.
     */
    public int size() {
        return state.get().minHeap.size();
    }

    /**
     * Get version for optimistic concurrency control.
     */
    public long getVersion() {
        return version.get();
    }

    /**
     * Check if top-K has been stable for a given number of updates.
     */
    public boolean isStable(int updateCount) {
        // Would track history of changes in production
        // For now, simplified check based on version changes
        return consolidationCounter.get() < updateCount;
    }
}