// ExpectedUtilityCalculator.java
package edu.thesis.mining.algorithms;

import edu.thesis.mining.core.*;
import java.util.*;

/**
 * Computes expected utility for itemsets under uncertainty.
 * Implements Algorithm 3 from the paper.
 */
public class ExpectedUtilityCalculator {
    private final ProbabilisticUtilityTensor PUT;
    private final UPTree upTree;

    // Configuration parameters
    private static final double ALPHA = 0.1;  // Uncertainty discount factor
    private static final boolean ENABLE_SYNERGY = true;  // Enable utility dependencies

    public ExpectedUtilityCalculator(ProbabilisticUtilityTensor PUT, UPTree upTree) {
        this.PUT = PUT;
        this.upTree = upTree;
    }

    /**
     * Compute the expected utility of an itemset under uncertainty.
     */
    public double compute(Itemset itemset) {
        // Check cache first
        Double cached = PUT.getCachedEU(itemset.getItems());
        if (cached != null) {
            return cached;
        }

        double expectedUtility = 0.0;

        // Get relevant transactions containing all items in the itemset
        List<Transaction> relevantTransactions = upTree.getTransactions(itemset.getItems());

        // If using PUT's method instead
        if (relevantTransactions.isEmpty()) {
            relevantTransactions = PUT.getItemsetTransactions(itemset.getItems());
        }

        // Process each relevant transaction
        for (Transaction transaction : relevantTransactions) {
            // Calculate P(T) - probability that transaction exists
            double probTransactionExists = transaction.getExistenceProbability();

            // Calculate P(X⊆T|T) - probability that itemset appears in transaction given it exists
            double probItemsetInTransaction = calculateItemsetProbability(itemset, transaction);

            // Calculate u(X,T) - utility of itemset in this transaction
            double utilityInTransaction = calculateItemsetUtility(itemset, transaction);

            // Handle utility dependencies/synergies if enabled
            if (ENABLE_SYNERGY && hasUtilityDependencies(itemset, transaction)) {
                double synergyBonus = computeSynergyUtility(itemset, transaction);
                utilityInTransaction += synergyBonus;
            }

            // Accumulate expected utility contribution from this transaction
            double contribution = probTransactionExists *
                                probItemsetInTransaction *
                                utilityInTransaction;
            expectedUtility += contribution;
        }

        // Apply uncertainty discount factor for high-variance itemsets
        double variance = computeUtilityVariance(itemset, relevantTransactions);
        double uncertaintyFactor = 1.0 / (1.0 + ALPHA * variance);
        expectedUtility *= uncertaintyFactor;

        // Cache the result for future use
        PUT.cacheItemsetEU(itemset.getItems(), expectedUtility);

        return expectedUtility;
    }

    /**
     * Calculate the probability that all items in the itemset appear in the transaction.
     * This is the product of individual item probabilities (assuming independence).
     */
    private double calculateItemsetProbability(Itemset itemset, Transaction transaction) {
        double probability = 1.0;

        for (String item : itemset.getItems()) {
            double itemProb = transaction.getItemProbability(item);
            probability *= itemProb;

            // Early termination if probability becomes too small
            if (probability < 1e-10) {
                return 0.0;
            }
        }

        return probability;
    }

    /**
     * Calculate the total utility of the itemset in the transaction.
     * This sums individual item utilities (can be positive or negative).
     */
    private double calculateItemsetUtility(Itemset itemset, Transaction transaction) {
        double totalUtility = 0.0;

        for (String item : itemset.getItems()) {
            double itemUtility = transaction.getItemUtility(item);
            totalUtility += itemUtility;
        }

        return totalUtility;
    }

    /**
     * Check if there are utility dependencies between items in the itemset.
     * For example, buying a printer and ink together might have extra utility.
     */
    private boolean hasUtilityDependencies(Itemset itemset, Transaction transaction) {
        // This is domain-specific. Here we check for known synergistic pairs
        Set<String> items = itemset.getItems();

        // Example: Check for complementary items
        if (items.contains("printer") && items.contains("ink")) {
            return true;
        }

        if (items.contains("camera") && items.contains("memory_card")) {
            return true;
        }

        // Could be extended with a dependency matrix or learned patterns
        return false;
    }

    /**
     * Compute synergy utility when items have dependencies.
     * This represents additional utility gained from item combinations.
     */
    private double computeSynergyUtility(Itemset itemset, Transaction transaction) {
        double synergyBonus = 0.0;
        Set<String> items = itemset.getItems();

        // Define synergy bonuses for known combinations
        if (items.contains("printer") && items.contains("ink")) {
            // 10% bonus on the smaller utility
            double printerUtil = transaction.getItemUtility("printer");
            double inkUtil = transaction.getItemUtility("ink");
            synergyBonus = 0.1 * Math.min(Math.abs(printerUtil), Math.abs(inkUtil));
        }

        if (items.contains("camera") && items.contains("memory_card")) {
            // Fixed bonus for complementary items
            synergyBonus += 5.0;
        }

        // Could use more sophisticated models like:
        // - Learned interaction effects from historical data
        // - Domain-specific business rules
        // - Customer segment-based synergies

        return synergyBonus;
    }

    /**
     * Compute the variance of utility values across transactions.
     * High variance indicates uncertainty in the expected utility estimate.
     */
    private double computeUtilityVariance(Itemset itemset, List<Transaction> transactions) {
        if (transactions.size() < 2) {
            return 0.0;
        }

        // First, compute the mean utility
        double sum = 0.0;
        double weightSum = 0.0;

        for (Transaction t : transactions) {
            double weight = t.getExistenceProbability() *
                          calculateItemsetProbability(itemset, t);
            double utility = calculateItemsetUtility(itemset, t);

            sum += weight * utility;
            weightSum += weight;
        }

        double mean = weightSum > 0 ? sum / weightSum : 0.0;

        // Then compute variance
        double varianceSum = 0.0;

        for (Transaction t : transactions) {
            double weight = t.getExistenceProbability() *
                          calculateItemsetProbability(itemset, t);
            double utility = calculateItemsetUtility(itemset, t);
            double deviation = utility - mean;

            varianceSum += weight * deviation * deviation;
        }

        double variance = weightSum > 0 ? varianceSum / weightSum : 0.0;

        // Normalize by the squared mean to get coefficient of variation
        if (Math.abs(mean) > 1e-10) {
            variance = variance / (mean * mean);
        }

        return variance;
    }
}