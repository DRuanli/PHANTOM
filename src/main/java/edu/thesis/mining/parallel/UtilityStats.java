// UtilityStats.java
package edu.thesis.mining.parallel;

/**
 * Statistics about utility distribution in the database.
 * Used for intelligent search space partitioning.
 */
public class UtilityStats {
    private final int positiveCount;
    private final int negativeCount;
    private final double totalPositiveUtility;
    private final double totalNegativeUtility;

    public UtilityStats(int posCount, int negCount,
                       double totalPos, double totalNeg) {
        this.positiveCount = posCount;
        this.negativeCount = negCount;
        this.totalPositiveUtility = totalPos;
        this.totalNegativeUtility = totalNeg;
    }

    public double getNegativeRatio() {
        int total = positiveCount + negativeCount;
        return total > 0 ? (double) negativeCount / total : 0.0;
    }

    public double getAveragePositiveUtility() {
        return positiveCount > 0 ? totalPositiveUtility / positiveCount : 0.0;
    }

    public double getAverageNegativeUtility() {
        return negativeCount > 0 ? totalNegativeUtility / negativeCount : 0.0;
    }
}