package com.happytrade.market.indicator;

/**
 * Simple moving average.
 *
 * <p>Pure function with no framework dependencies so it can be unit-tested directly and reused by
 * the future AI-signal engine unchanged.
 */
public final class Sma {

    private Sma() {
    }

    /**
     * @return an array the same length as {@code values}; {@code null} at every index below
     *         {@code period - 1}, where the average is undefined.
     */
    public static Double[] calculate(double[] values, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive, got " + period);
        }

        Double[] result = new Double[values.length];
        double window = 0;

        for (int i = 0; i < values.length; i++) {
            window += values[i];
            if (i >= period) {
                window -= values[i - period];
            }
            if (i >= period - 1) {
                result[i] = window / period;
            }
        }

        return result;
    }
}
