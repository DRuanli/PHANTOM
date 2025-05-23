// UncertainMine.java
package edu.thesis.mining.algorithms;

import edu.thesis.mining.core.*;
import edu.thesis.mining.parallel.SearchPartition;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core mining algorithm for uncertain databases with mixed utilities.
 * Implements Algorithm 2 from the paper.
 */
public class UncertainMine {
    private final SearchPartition partition;
    private final ProbabilisticUtilityTensor PUT;
    private final AtomicReference<Double> threshold;
    private final Database localDB;
    private final UPTree upTree;

    // Algorithm parameters
    private static final double SPECULATION_FACTOR = 1.2;
    private static final int MAX_ITEMSET_SIZE = 20;

    public UncertainMine(SearchPartition partition,
                        ProbabilisticUtilityTensor PUT,
                        AtomicReference<Double> threshold,
                        Database localDB) {
        this.partition = partition;
        this.PUT = PUT;
        this.threshold = threshold;
        this.localDB = localDB;
        this.upTree = new UPTree(localDB);
    }

    /**
     * Mine high-utility itemsets from the assigned partition.
     */
    public List<Itemset> mine() {
        List<Itemset> candidates = new ArrayList<>();

        // Initialize with promising 1-itemsets
        List<Itemset> F1 = generateSingleItemsets();
        candidates.addAll(F1);

        // Iterative pattern growth
        List<Itemset> currentLevel = F1;

        for (int length = 2; length <= MAX_ITEMSET_SIZE && !currentLevel.isEmpty(); length++) {
            List<Itemset> nextLevel = new ArrayList<>();

            // Generate candidate pairs from current level
            List<Itemset> candidatePairs = generateCandidates(currentLevel);

            for (Itemset candidate : candidatePairs) {
                // Check if we should terminate
                if (partition.shouldTerminate()) {
                    return candidates;
                }

                // Compute expected utility under uncertainty
                ExpectedUtilityCalculator euCalc = new ExpectedUtilityCalculator(PUT, upTree);
                double eu = euCalc.compute(candidate);
                candidate.setExpectedUtility(eu);

                // Handle negative utilities with specialized bounds
                if (containsNegativeItems(candidate)) {
                    candidate.setContainsNegativeUtility(true);
                    PolarBoundsCalculator boundsCalc = new PolarBoundsCalculator(PUT, localDB);
                    double upperBound = boundsCalc.computeUpperBound(candidate);
                    double lowerBound = boundsCalc.computeLowerBound(candidate);

                    candidate.setUpperBound(upperBound);
                    candidate.setLowerBound(lowerBound);

                    // Prune if upper bound is below threshold
                    if (upperBound < threshold.get()) {
                        continue;
                    }
                } else {
                    // Standard probabilistic upper bound for positive utilities
                    double upperBound = computeProbabilisticUpperBound(candidate);
                    candidate.setUpperBound(upperBound);

                    if (upperBound < threshold.get()) {
                        continue;
                    }
                }

                // Add to candidates if expected utility exceeds threshold
                if (eu >= threshold.get()) {
                    candidates.add(candidate);
                    nextLevel.add(candidate);
                }

                // Speculative execution for promising supersets
                if (isSpeculativelyPromising(candidate)) {
                    List<Itemset> speculative = speculativeExplore(candidate);
                    candidates.addAll(speculative);
                }

                // Update partition progress
                partition.incrementProcessedCount();
            }

            currentLevel = nextLevel;
        }

        return candidates;
    }

    /**
     * Generate promising 1-itemsets from the partition.
     */
    private List<Itemset> generateSingleItemsets() {
        List<Itemset> F1 = new ArrayList<>();

        for (String item : partition.getItems()) {
            double eu = PUT.getSingleItemEU(item);

            if (eu >= threshold.get()) {
                Itemset itemset = new Itemset();
                itemset.addItem(item);
                itemset.setExpectedUtility(eu);

                // Check if this is a negative utility item
                if (hasNegativeUtility(item)) {
                    itemset.setContainsNegativeUtility(true);
                }

                F1.add(itemset);
            }
        }

        // Sort by expected utility for better candidate generation
        Collections.sort(F1);

        return F1;
    }

    /**
     * Generate candidate itemsets by combining itemsets from the current level.
     */
    private List<Itemset> generateCandidates(List<Itemset> currentLevel) {
        List<Itemset> candidates = new ArrayList<>();

        // Use Apriori-style candidate generation with pruning
        for (int i = 0; i < currentLevel.size(); i++) {
            for (int j = i + 1; j < currentLevel.size(); j++) {
                Itemset itemset1 = currentLevel.get(i);
                Itemset itemset2 = currentLevel.get(j);

                // Check if they can be combined (share k-1 items)
                Itemset combined = tryToCombine(itemset1, itemset2);
                if (combined != null) {
                    candidates.add(combined);
                }
            }
        }

        return candidates;
    }

    /**
     * Try to combine two itemsets if they share k-1 items.
     */
    private Itemset tryToCombine(Itemset itemset1, Itemset itemset2) {
        Set<String> items1 = itemset1.getItems();
        Set<String> items2 = itemset2.getItems();

        // They should have the same size
        if (items1.size() != items2.size()) {
            return null;
        }

        // Find the symmetric difference
        Set<String> diff1 = new HashSet<>(items1);
        diff1.removeAll(items2);

        Set<String> diff2 = new HashSet<>(items2);
        diff2.removeAll(items1);

        // Should differ by exactly one item each
        if (diff1.size() != 1 || diff2.size() != 1) {
            return null;
        }

        // Create the combined itemset
        Itemset combined = new Itemset(items1);
        combined.addItem(diff2.iterator().next());

        // Inherit negative utility flag if either parent has it
        if (itemset1.containsNegativeUtility() || itemset2.containsNegativeUtility()) {
            combined.setContainsNegativeUtility(true);
        }

        return combined;
    }

    /**
     * Check if an itemset contains items with negative utilities.
     */
    private boolean containsNegativeItems(Itemset itemset) {
        for (String item : itemset.getItems()) {
            if (hasNegativeUtility(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a single item has negative utility in any transaction.
     */
    private boolean hasNegativeUtility(String item) {
        List<Transaction> transactions = PUT.getItemTransactions(item);
        for (Transaction t : transactions) {
            if (t.getItemUtility(item) < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compute probabilistic upper bound for itemsets with only positive utilities.
     */
    private double computeProbabilisticUpperBound(Itemset itemset) {
        double currentEU = itemset.getExpectedUtility();
        double maxAdditionalUtility = 0.0;

        // Find items that could be added
        Set<String> currentItems = itemset.getItems();
        Set<String> remainingItems = new HashSet<>(partition.getItems());
        remainingItems.removeAll(currentItems);

        // For each remaining item, compute its maximum possible contribution
        for (String item : remainingItems) {
            double maxContribution = 0.0;

            // Only consider positive utility items for upper bound
            if (!hasNegativeUtility(item)) {
                List<Transaction> transactions = PUT.getItemsetTransactions(currentItems);

                for (Transaction t : transactions) {
                    if (t.containsItem(item)) {
                        double contribution = t.getExistenceProbability() *
                                            t.getItemProbability(item) *
                                            t.getItemUtility(item);
                        maxContribution = Math.max(maxContribution, contribution);
                    }
                }
            }

            maxAdditionalUtility += maxContribution;
        }

        return currentEU + maxAdditionalUtility;
    }

    /**
     * Determine if an itemset is promising enough for speculative exploration.
     */
    private boolean isSpeculativelyPromising(Itemset itemset) {
        // Speculate if utility is within SPECULATION_FACTOR of threshold
        // and the itemset size is small enough
        return itemset.getExpectedUtility() >= threshold.get() * SPECULATION_FACTOR &&
               itemset.size() < MAX_ITEMSET_SIZE / 2;
    }

    /**
     * Speculatively explore supersets of a promising itemset.
     */
    private List<Itemset> speculativeExplore(Itemset baseItemset) {
        List<Itemset> speculative = new ArrayList<>();

        // Try adding each remaining item from the partition
        Set<String> currentItems = baseItemset.getItems();
        Set<String> remainingItems = new HashSet<>(partition.getItems());
        remainingItems.removeAll(currentItems);

        // Limit speculative exploration to avoid explosion
        int explorationCount = 0;
        final int MAX_SPECULATION = 10;

        for (String item : remainingItems) {
            if (explorationCount >= MAX_SPECULATION) {
                break;
            }

            // Create speculative superset
            Itemset superset = new Itemset(baseItemset);
            superset.addItem(item);

            // Quick utility estimation
            ExpectedUtilityCalculator euCalc = new ExpectedUtilityCalculator(PUT, upTree);
            double eu = euCalc.compute(superset);
            superset.setExpectedUtility(eu);

            if (eu >= threshold.get()) {
                speculative.add(superset);
                explorationCount++;
            }
        }

        return speculative;
    }
}