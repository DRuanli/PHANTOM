// Transaction.java
package edu.thesis.mining.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents an uncertain transaction in the database.
 * Each transaction has an existence probability and items with individual probabilities and utilities.
 */
public class Transaction {
    private final String tid;  // Transaction ID
    private final double existenceProbability;  // P(T)
    private final Map<String, Double> itemProbabilities;  // P(item|T)
    private final Map<String, Double> itemUtilities;  // Can be positive or negative

    public Transaction(String tid, double existenceProbability) {
        this.tid = tid;
        this.existenceProbability = existenceProbability;
        this.itemProbabilities = new HashMap<>();
        this.itemUtilities = new HashMap<>();
    }

    public void addItem(String item, double probability, double utility) {
        itemProbabilities.put(item, probability);
        itemUtilities.put(item, utility);
    }

    // Getters
    public String getTid() { return tid; }
    public double getExistenceProbability() { return existenceProbability; }
    public double getItemProbability(String item) {
        return itemProbabilities.getOrDefault(item, 0.0);
    }
    public double getItemUtility(String item) {
        return itemUtilities.getOrDefault(item, 0.0);
    }
    public Set<String> getItems() { return itemProbabilities.keySet(); }

    public boolean containsItem(String item) {
        return itemProbabilities.containsKey(item);
    }
}
