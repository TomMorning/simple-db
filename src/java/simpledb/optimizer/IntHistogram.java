package simpledb.optimizer;

import simpledb.execution.Predicate;

/**
 * A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {

    private final int[] buckets;
    private final int min;
    private final int max;
    private final double bucketWidth;
    private int totalValues;

    /**
     * Create a new IntHistogram.
     * <p>
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * <p>
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * <p>
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't
     * simply store every value that you see in a sorted list.
     *
     * @param buckets The number of buckets to split the input value into.
     * @param min     The minimum integer value that will ever be passed to this class for histogramming
     * @param max     The maximum integer value that will ever be passed to this class for histogramming
     */
    public IntHistogram(int buckets, int min, int max) {
        // TODO: some code goes here
        this.buckets = new int[buckets];
        this.min = min;
        this.max = max;
        this.bucketWidth = (double) (max - min + 1) / buckets;
        this.totalValues = 0;
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     *
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
        // TODO: some code goes here
        int index = (int) Math.floor((v - min) / bucketWidth);
        buckets[index]++;
        totalValues++;
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * <p>
     * For example, if "op" is "GREATER_THAN" and "v" is 5,
     * return your estimate of the fraction of elements that are greater than 5.
     *
     * @param op Operator
     * @param v  Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {

        // TODO: some code goes here
        if (totalValues == 0) {
            return 0.0;
        }
        int index = (int) Math.floor((v - min) / bucketWidth);
        double selectivity = 0.0;
        switch (op) {
            case EQUALS:
                if (v < min || v > max) {
                    return 0.0;
                }
                selectivity = (double) buckets[index] / bucketWidth;
                break;
            case GREATER_THAN:
                if (v < min) {
                    return 1.0;
                }
                if (v > max) {
                    return 0.0;
                }
                for (int i = index + 1; i < buckets.length; i++) {
                    selectivity += buckets[i];
                }
                selectivity += (buckets[index] / bucketWidth) * (bucketWidth - (v - min) % bucketWidth) / bucketWidth;
                break;
            case LESS_THAN:
                if (v <= min) {
                    return 0.0;
                }
                if (v > max) {
                    return 1.0;
                }
                for (int i = 0; i < index; i++) {
                    selectivity += buckets[i];
                }
                selectivity += (buckets[index] / bucketWidth) * ((v - min) % bucketWidth) / bucketWidth;
                break;
            case GREATER_THAN_OR_EQ:
                if (v <= min) {
                    return 1.0;
                }
                if (v > max) {
                    return 0.0;
                }
                return estimateSelectivity(Predicate.Op.GREATER_THAN, v) + estimateSelectivity(Predicate.Op.EQUALS, v);
            case LESS_THAN_OR_EQ:
                if (v < min) {
                    return 0.0;
                }
                if (v >= max) {
                    return 1.0;
                }
                return estimateSelectivity(Predicate.Op.LESS_THAN, v) + estimateSelectivity(Predicate.Op.EQUALS, v);
            case NOT_EQUALS:
                return 1.0 - estimateSelectivity(Predicate.Op.EQUALS, v);
            default:
                return -1.0;
        }
        return selectivity / totalValues;
    }

    /**
     * @return the average selectivity of this histogram.
     *         <p>
     *         This is not an indispensable method to implement the basic
     *         join optimization. It may be needed if you want to
     *         implement a more efficient optimization
     */
    public double avgSelectivity() {
        // TODO: some code goes here
        if (totalValues == 0) {
            return 0.0;
        }

        double avgSelectivity = 0.0;

        for (int i = 0; i < buckets.length; i++) {
            avgSelectivity += (double) buckets[i] / totalValues;
        }
        return avgSelectivity / buckets.length;
    }

    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        // TODO: some code goes here
        StringBuilder sb = new StringBuilder();
        sb.append("IntHistogram: ");
        for (int i = 0; i < buckets.length; i++) {
            sb.append(String.format("Bucket %d: %d, ", i, buckets[i]));
        }
        return sb.toString();
    }
}
