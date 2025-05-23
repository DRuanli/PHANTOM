// Itemset.java
package edu.thesis.mining.core;

import java.util.*;

/**
 * Represents an itemset with its expected utility and bounds.
 * Implements Comparable for easy sorting by utility.
 */
public class Itemset implements Comparable<Itemset> {
    private final Set<String> items;
    private double expectedUtility;
    private double upperBound;
    private double lowerBound;
    private boolean containsNegativeUtility;

    public Itemset() {
        this.items = new HashSet<>();
        this.expectedUtility = 0.0;
        this.upperBound = Double.POSITIVE_INFINITY;
        this.lowerBound = Double.NEGATIVE_INFINITY;
        this.containsNegativeUtility = false;
    }

    public Itemset(Set<String> items) {
        this.items = new HashSet<>(items);
        this.expectedUtility = 0.0;
        this.upperBound = Double.POSITIVE_INFINITY;
        this.lowerBound = Double.NEGATIVE_INFINITY;
        this.containsNegativeUtility = false;
    }

    // Deep copy constructor
    public Itemset(Itemset other) {
        this.items = new HashSet<>(other.items);
        this.expectedUtility = other.expectedUtility;
        this.upperBound = other.upperBound;
        this.lowerBound = other.lowerBound;
        this.containsNegativeUtility = other.containsNegativeUtility;
    }

    public void addItem(String item) {
        items.add(item);
    }

    public boolean containsAll(Collection<String> otherItems) {
        return items.containsAll(otherItems);
    }

    @Override
    public int compareTo(Itemset other) {
        // Sort in descending order of expected utility
        return Double.compare(other.expectedUtility, this.expectedUtility);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Itemset)) return false;
        Itemset other = (Itemset) obj;
        return items.equals(other.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    // Getters and setters
    public Set<String> getItems() { return new HashSet<>(items); }
    public double getExpectedUtility() { return expectedUtility; }
    public void setExpectedUtility(double eu) { this.expectedUtility = eu; }
    public double getUpperBound() { return upperBound; }
    public void setUpperBound(double ub) { this.upperBound = ub; }
    public double getLowerBound() { return lowerBound; }
    public void setLowerBound(double lb) { this.lowerBound = lb; }
    public boolean containsNegativeUtility() { return containsNegativeUtility; }
    public void setContainsNegativeUtility(boolean contains) {
        this.containsNegativeUtility = contains;
    }
    public int size() { return items.size(); }
}
