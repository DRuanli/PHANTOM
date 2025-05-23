// Database.java
package edu.thesis.mining.core;

import java.util.*;

/**
 * Represents the uncertain database with utility values.
 */
public class Database {
    private final List<Transaction> transactions;
    private final Set<String> allItems;

    public Database() {
        this.transactions = new ArrayList<>();
        this.allItems = new HashSet<>();
    }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        allItems.addAll(transaction.getItems());
    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public Set<String> getAllItems() {
        return new HashSet<>(allItems);
    }

    public int size() {
        return transactions.size();
    }

    public int getItemCount() {
        return allItems.size();
    }

    /**
     * Get a partition of the database for a specific processor.
     */
    public Database getPartition(int processorId, int numProcessors) {
        Database partition = new Database();

        for (int i = processorId; i < transactions.size(); i += numProcessors) {
            partition.addTransaction(transactions.get(i));
        }

        return partition;
    }
}