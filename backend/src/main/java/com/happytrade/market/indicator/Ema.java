package com.happytrade.market.indicator;

/**
 * Exponential moving average.
 *
 * <p>Seeded with the simple moving average of the first {@code period} values rather than with the
 * first value alone. Seeding from a single value makes early output depend on how much history
 * happened to be fetched, which would make the same candle produce different numbers on different
 * requests.
 */
public final class Ema {

    private Ema() {
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
        if (values.length < period) {
            return result;
        }

        double seed = 0;
        for (int i = 0; i < period; i++) {
            seed += values[i];
        }

        double multiplier = 2.0 / (period + 1);
        double previous = seed / period;
        result[period - 1] = previous;

        for (int i = period; i < values.length; i++) {
            previous = values[i] * multiplier + previous * (1 - multiplier);
            result[i] = previous;
        }

        return result;
    }
}
