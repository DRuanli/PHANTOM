// ProbabilisticUtilityTensor.java
package edu.thesis.mining.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Efficient data structure for storing precomputed utility distributions.
 * This enables fast expected utility calculations during mining.
 */
public class ProbabilisticUtilityTensor {
    private final Map<String, Double> singleItemEU;  // Expected utilities for single items
    private final Map<Set<String>, Double> itemsetEU;  // Cached expected utilities
    private final Map<String, List<Transaction>> itemTransactions;  // Inverted index
    private final Database database;

    public ProbabilisticUtilityTensor(Database database) {
        this.database = database;
        this.singleItemEU = new ConcurrentHashMap<>();
        this.itemsetEU = new ConcurrentHashMap<>();
        this.itemTransactions = new ConcurrentHashMap<>();

        // Build inverted index and compute single item utilities
        buildIndex();
    }

    private void buildIndex() {
        // First pass: build inverted index
        for (Transaction t : database.getTransactions()) {
            for (String item : t.getItems()) {
                itemTransactions.computeIfAbsent(item, k -> new ArrayList<>()).add(t);
            }
        }

        // Second pass: compute single item expected utilities
        for (String item : itemTransactions.keySet()) {
            double eu = computeSingleItemEU(item);
            singleItemEU.put(item, eu);
        }
    }

    private double computeSingleItemEU(String item) {
        double eu = 0.0;
        List<Transaction> transactions = itemTransactions.get(item);

        for (Transaction t : transactions) {
            double contribution = t.getExistenceProbability() *
                                t.getItemProbability(item) *
                                t.getItemUtility(item);
            eu += contribution;
        }

        return eu;
    }

    public double getSingleItemEU(String item) {
        return singleItemEU.getOrDefault(item, 0.0);
    }

    public List<Transaction> getItemTransactions(String item) {
        return itemTransactions.getOrDefault(item, Collections.emptyList());
    }

    // Get transactions containing all items in the itemset
    public List<Transaction> getItemsetTransactions(Set<String> itemset) {
        if (itemset.isEmpty()) return Collections.emptyList();

        // Start with transactions of the least frequent item
        String leastFrequent = findLeastFrequentItem(itemset);
        List<Transaction> result = new ArrayList<>(itemTransactions.get(leastFrequent));

        // Filter by other items
        for (String item : itemset) {
            if (!item.equals(leastFrequent)) {
                result.removeIf(t -> !t.containsItem(item));
            }
        }

        return result;
    }

    private String findLeastFrequentItem(Set<String> itemset) {
        String leastFrequent = null;
        int minCount = Integer.MAX_VALUE;

        for (String item : itemset) {
            int count = itemTransactions.getOrDefault(item, Collections.emptyList()).size();
            if (count < minCount) {
                minCount = count;
                leastFrequent = item;
            }
        }

        return leastFrequent;
    }

    // Cache expected utility for reuse
    public void cacheItemsetEU(Set<String> itemset, double eu) {
        itemsetEU.put(new HashSet<>(itemset), eu);
    }

    public Double getCachedEU(Set<String> itemset) {
        return itemsetEU.get(itemset);
    }
}