// UPTree.java
package edu.thesis.mining.core;

import java.util.*;

/**
 * Utility Pattern Tree for efficient transaction retrieval.
 */
public class UPTree {
    private final Map<Set<String>, List<Transaction>> itemsetTransactions;
    private final Database database;

    public UPTree(Database database) {
        this.database = database;
        this.itemsetTransactions = new HashMap<>();
        buildTree();
    }

    private void buildTree() {
        // Index transactions by items for efficient retrieval
        for (Transaction t : database.getTransactions()) {
            Set<String> items = t.getItems();

            // Add to all subset indices
            for (String item : items) {
                Set<String> key = Collections.singleton(item);
                itemsetTransactions.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
            }
        }
    }

    public List<Transaction> getTransactions(Set<String> itemset) {
        // For now, use simple filtering. In production, use more efficient index
        List<Transaction> result = new ArrayList<>();

        for (Transaction t : database.getTransactions()) {
            boolean containsAll = true;
            for (String item : itemset) {
                if (!t.containsItem(item)) {
                    containsAll = false;
                    break;
                }
            }
            if (containsAll) {
                result.add(t);
            }
        }

        return result;
    }
}