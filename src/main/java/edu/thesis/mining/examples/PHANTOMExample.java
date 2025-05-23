// PHANTOMExample.java
package edu.thesis.mining.examples;

import edu.thesis.mining.core.*;
import edu.thesis.mining.parallel.*;
import org.apache.commons.cli.*;
import java.io.*;
import java.util.*;

/**
 * Example program demonstrating PHANTOM algorithm usage.
 * Supports both programmatic and file-based input.
 */
public class PHANTOMExample {

    public static void main(String[] args) throws Exception {
        // Parse command line arguments
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        // Get parameters
        int K = Integer.parseInt(cmd.getOptionValue("k", "10"));
        int processors = Integer.parseInt(cmd.getOptionValue("p", "4"));
        String inputFile = cmd.getOptionValue("i", null);
        String outputFile = cmd.getOptionValue("o", "results/output.txt");

        // Create or load database
        Database database;
        if (inputFile != null) {
            System.out.println("Loading database from: " + inputFile);
            database = loadDatabaseFromFile(inputFile);
        } else {
            System.out.println("Using sample database");
            database = createSampleDatabase();
        }

        // Run PHANTOM algorithm
        System.out.println("\nStarting PHANTOM algorithm:");
        System.out.println("- K = " + K);
        System.out.println("- Processors = " + processors);
        System.out.println("- Transactions = " + database.size());
        System.out.println("- Items = " + database.getItemCount());
        System.out.println();

        long startTime = System.currentTimeMillis();
        PHANTOMMain phantom = new PHANTOMMain(database, K, processors);
        List<Itemset> topK = phantom.mine();
        long endTime = System.currentTimeMillis();

        // Display and save results
        displayResults(topK, K);
        saveResults(topK, outputFile, endTime - startTime);

        System.out.println("\nTotal execution time: " +
                          (endTime - startTime) / 1000.0 + " seconds");
    }

    private static Options createOptions() {
        Options options = new Options();

        options.addOption("k", "topk", true,
            "Number of top itemsets to find (default: 10)");
        options.addOption("p", "processors", true,
            "Number of parallel processors (default: 4)");
        options.addOption("i", "input", true,
            "Input file path (optional)");
        options.addOption("o", "output", true,
            "Output file path (default: results/output.txt)");
        options.addOption("h", "help", false,
            "Show help message");

        return options;
    }

    private static Database loadDatabaseFromFile(String filename)
            throws IOException {
        Database database = new Database();

        try (BufferedReader reader = new BufferedReader(
                new FileReader(filename))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }

                // Parse transaction line
                // Format: TID existence_prob item1:prob1:utility1 item2:prob2:utility2 ...
                String[] parts = line.split("\\s+");
                String tid = parts[0];
                double existenceProb = Double.parseDouble(parts[1]);

                Transaction transaction = new Transaction(tid, existenceProb);

                for (int i = 2; i < parts.length; i++) {
                    String[] itemData = parts[i].split(":");
                    String item = itemData[0];
                    double prob = Double.parseDouble(itemData[1]);
                    double utility = Double.parseDouble(itemData[2]);

                    transaction.addItem(item, prob, utility);
                }

                database.addTransaction(transaction);
            }
        }

        return database;
    }

    private static Database createSampleDatabase() {
        Database db = new Database();
        Random rand = new Random(42); // Fixed seed for reproducibility

        // Create realistic e-commerce transactions
        String[] items = {
            "laptop", "mouse", "keyboard", "monitor", "hdmi_cable",
            "warranty", "software", "bag", "webcam", "headphones",
            "discount_coupon", "insurance", "express_shipping"
        };

        double[] baseUtilities = {
            500, 20, 50, 300, 15,
            -50, 100, 40, 80, 60,
            -100, -30, -20
        };

        // Generate 1000 transactions
        for (int i = 0; i < 1000; i++) {
            double existenceProb = 0.7 + rand.nextDouble() * 0.3;
            Transaction t = new Transaction("T" + i, existenceProb);

            // Each transaction has 3-8 items
            int numItems = 3 + rand.nextInt(6);
            Set<Integer> chosenItems = new HashSet<>();

            while (chosenItems.size() < numItems) {
                int itemIdx = rand.nextInt(items.length);
                if (!chosenItems.contains(itemIdx)) {
                    chosenItems.add(itemIdx);

                    double itemProb = 0.6 + rand.nextDouble() * 0.4;
                    double utility = baseUtilities[itemIdx] *
                                   (0.9 + rand.nextDouble() * 0.2);

                    t.addItem(items[itemIdx], itemProb, utility);
                }
            }

            db.addTransaction(t);
        }

        return db;
    }

    private static void displayResults(List<Itemset> topK, int K) {
        System.out.println("\n=== Top-" + K + " High-Utility Itemsets ===");

        for (int i = 0; i < topK.size(); i++) {
            Itemset itemset = topK.get(i);
            System.out.printf("\n%d. Itemset: %s%n", i + 1,
                            formatItemset(itemset.getItems()));
            System.out.printf("   Expected Utility: %.2f%n",
                            itemset.getExpectedUtility());
            System.out.printf("   Upper Bound: %.2f%n",
                            itemset.getUpperBound());

            if (itemset.containsNegativeUtility()) {
                System.out.println("   Contains negative utility items");
            }
        }
    }

    private static String formatItemset(Set<String> items) {
        List<String> sorted = new ArrayList<>(items);
        Collections.sort(sorted);
        return "{" + String.join(", ", sorted) + "}";
    }


    private static void saveResults(List<Itemset> topK, String filename,
                                  long executionTime) throws IOException {
        // Create directory if it doesn't exist
        new File(filename).getParentFile().mkdirs();

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("# PHANTOM Mining Results");
            writer.println("# Execution Time: " + executionTime + " ms");
            writer.println("# Timestamp: " + new Date());
            writer.println();

            for (int i = 0; i < topK.size(); i++) {
                Itemset itemset = topK.get(i);
                writer.printf("%d,%s,%.6f,%.6f,%b%n",
                    i + 1,
                    formatItemset(itemset.getItems()),
                    itemset.getExpectedUtility(),
                    itemset.getUpperBound(),
                    itemset.containsNegativeUtility()
                );
            }
        }

        System.out.println("\nResults saved to: " + filename);
    }
}