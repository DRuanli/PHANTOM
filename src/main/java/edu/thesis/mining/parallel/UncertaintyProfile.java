// UncertaintyProfile.java
package edu.thesis.mining.parallel;

/**
 * Captures uncertainty characteristics of the database.
 * This helps in intelligent partitioning and processing decisions.
 */
public class UncertaintyProfile {
    private final double averageExistenceProbability;
    private final double averageItemProbability;

    public UncertaintyProfile(double avgExistence, double avgItem) {
        this.averageExistenceProbability = avgExistence;
        this.averageItemProbability = avgItem;
    }

    public double getAverageUncertainty() {
        // Combined uncertainty metric
        return (averageExistenceProbability + averageItemProbability) / 2.0;
    }

    public double getAverageExistenceProbability() {
        return averageExistenceProbability;
    }

    public double getAverageItemProbability() {
        return averageItemProbability;
    }
}