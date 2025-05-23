// PolarBoundsCalculator.java
package edu.thesis.mining.algorithms;

import edu.thesis.mining.core.*;
import java.util.*;

/**
 * Computes polar bounds for itemsets containing negative utility items.
 * Implements Algorithm 4 from the paper.
 */
public class PolarBoundsCalculator {
    private final ProbabilisticUtilityTensor PUT;
    private final Database database;

    // Configuration parameters
    private static final double EPSILON = 0.8;  // Confidence factor for negative items
    private static final double OPTIMISM_FACTOR = 0.9;  // Reduce over-optimism

    public PolarBoundsCalculator(ProbabilisticUtilityTensor PUT, Database database) {
        this.PUT = PUT;
        this.database = database;
    }

    /**
     * Compute upper bound on utility for supersets of an itemset with negative utilities.
     * This bound must be carefully calculated to ensure no false negatives.
     */
    public double computeUpperBound(Itemset itemset) {
        // Separate positive and negative utility items
        Set<String> positiveItems = new HashSet<>();
        Set<String> negativeItems = new HashSet<>();

        for (String item : itemset.getItems()) {
            if (isPositiveUtilityItem(item)) {
                positiveItems.add(item);
            } else {
                negativeItems.add(item);
            }
        }

        // Compute base expected utility of current itemset
        ExpectedUtilityCalculator euCalc = new ExpectedUtilityCalculator(PUT, null);
        double baseEU = euCalc.compute(itemset);

        // Find remaining items that could be added
        Set<String> allItems = database.getAllItems();
        Set<String> remainingItems = new HashSet<>(allItems);
        remainingItems.removeAll(itemset.getItems());

        // Separate remaining items into positive and negative
        Set<String> remainingPositive = new HashSet<>();
        Set<String> remainingNegative = new HashSet<>();

        for (String item : remainingItems) {
            if (isPositiveUtilityItem(item)) {
                remainingPositive.add(item);
            } else {
                remainingNegative.add(item);
            }
        }

        // Compute optimistic gain from positive items
        double optimisticGain = computeOptimisticGain(itemset, remainingPositive);

        // Compute pessimistic loss from negative items
        double pessimisticLoss = computePessimisticLoss(itemset, remainingNegative);

        // Final upper bound combines base utility, potential gains, and minimal losses
        double upperBound = baseEU + optimisticGain * OPTIMISM_FACTOR + pessimisticLoss;

        return upperBound;
    }

    /**
     * Compute lower bound on utility for supersets of an itemset with negative utilities.
     * This represents the worst-case scenario.
     */
    public double computeLowerBound(Itemset itemset) {
        // Base expected utility
        ExpectedUtilityCalculator euCalc = new ExpectedUtilityCalculator(PUT, null);
        double baseEU = euCalc.compute(itemset);

        // Find all negative items that could be added
        Set<String> allItems = database.getAllItems();
        Set<String> remainingItems = new HashSet<>(allItems);
        remainingItems.removeAll(itemset.getItems());

        double worstCaseLoss = 0.0;

        // Consider worst case: all negative items are added with maximum impact
        for (String item : remainingItems) {
            if (!isPositiveUtilityItem(item)) {
                double maxNegativeImpact = computeMaxNegativeImpact(itemset, item);
                worstCaseLoss += maxNegativeImpact;
            }
        }

        return baseEU + worstCaseLoss;
    }

    /**
     * Check if an item generally has positive utility across transactions.
     */
    private boolean isPositiveUtilityItem(String item) {
        double totalUtility = 0.0;
        int count = 0;

        List<Transaction> transactions = PUT.getItemTransactions(item);
        for (Transaction t : transactions) {
            totalUtility += t.getItemUtility(item);
            count++;
        }

        return count > 0 && totalUtility / count > 0;
    }

    /**
     * Compute optimistic gain from adding positive utility items.
     */
    private double computeOptimisticGain(Itemset currentItemset, Set<String> remainingPositive) {
        double totalGain = 0.0;

        for (String item : remainingPositive) {
            double maxContribution = 0.0;

            // Find transactions where this item could be added to the current itemset
            List<Transaction> itemsetTransactions = PUT.getItemsetTransactions(currentItemset.getItems());

            for (Transaction t : itemsetTransactions) {
                if (t.containsItem(item)) {
                    // Calculate the maximum possible contribution
                    double probContribution = t.getExistenceProbability() *
                                            t.getItemProbability(item) *
                                            t.getItemUtility(item);

                    // Consider correlation with existing items
                    double correlationFactor = estimateCorrelation(currentItemset.getItems(), item, t);
                    probContribution *= correlationFactor;

                    maxContribution = Math.max(maxContribution, probContribution);
                }
            }

            totalGain += maxContribution;
        }

        return totalGain;
    }

    /**
     * Compute pessimistic loss from potentially adding negative utility items.
     */
    private double computePessimisticLoss(Itemset currentItemset, Set<String> remainingNegative) {
        double totalLoss = 0.0;

        for (String item : remainingNegative) {
            double minLoss = 0.0;

            // Check if this negative item could appear with the current itemset
            if (couldAppearTogether(currentItemset.getItems(), item)) {
                List<Transaction> itemsetTransactions = PUT.getItemsetTransactions(currentItemset.getItems());

                for (Transaction t : itemsetTransactions) {
                    if (t.containsItem(item)) {
                        // Calculate minimum negative impact (least negative contribution)
                        double probLoss = t.getExistenceProbability() *
                                        t.getItemProbability(item) *
                                        Math.abs(t.getItemUtility(item)) * EPSILON;

                        minLoss = Math.min(minLoss, -probLoss);
                    }
                }
            }

            totalLoss += minLoss;
        }

        return totalLoss;
    }

    /**
     * Compute maximum negative impact of adding a negative item.
     */
    private double computeMaxNegativeImpact(Itemset currentItemset, String negativeItem) {
        double maxImpact = 0.0;

        List<Transaction> itemsetTransactions = PUT.getItemsetTransactions(currentItemset.getItems());

        for (Transaction t : itemsetTransactions) {
            if (t.containsItem(negativeItem)) {
                double impact = t.getExistenceProbability() *
                              t.getItemProbability(negativeItem) *
                              Math.abs(t.getItemUtility(negativeItem));

                maxImpact = Math.max(maxImpact, impact);
            }
        }

        return -maxImpact;  // Return as negative since it's a loss
    }

    /**
     * Check if an item could potentially appear together with an itemset.
     * This uses association rules or domain knowledge.
     */
    private boolean couldAppearTogether(Set<String> itemset, String item) {
        // Check if they have ever appeared together
        List<Transaction> itemTransactions = PUT.getItemTransactions(item);

        for (Transaction t : itemTransactions) {
            boolean containsAll = true;
            for (String existingItem : itemset) {
                if (!t.containsItem(existingItem)) {
                    containsAll = false;
                    break;
                }
            }
            if (containsAll) {
                return true;
            }
        }

        // Could also use domain knowledge or association rules
        // For example, competing brands might not appear together

        return false;
    }

    /**
     * Estimate correlation between existing items and a new item.
     * Higher correlation means they're more likely to appear together.
     */
    private double estimateCorrelation(Set<String> existingItems, String newItem, Transaction t) {
        // Simple correlation: fraction of transactions containing both
        int coOccurrences = 0;
        int newItemOccurrences = 0;

        List<Transaction> allTransactions = database.getTransactions();

        for (Transaction trans : allTransactions) {
            if (trans.containsItem(newItem)) {
                newItemOccurrences++;

                boolean hasAllExisting = true;
                for (String existing : existingItems) {
                    if (!trans.containsItem(existing)) {
                        hasAllExisting = false;
                        break;
                    }
                }

                if (hasAllExisting) {
                    coOccurrences++;
                }
            }
        }

        if (newItemOccurrences == 0) {
            return 0.0;
        }

        return (double) coOccurrences / newItemOccurrences;
    }
}