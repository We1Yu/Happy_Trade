package com.happytrade.market.indicator;

/**
 * Relative Strength Index using Wilder's smoothing.
 *
 * <p>The first average gain and average loss are the simple means of the first {@code period}
 * gains and losses, so the first defined value lands at index {@code period}. Each average is then
 * smoothed against its own series.
 */
public final class Rsi {

    private Rsi() {
    }

    /**
     * @return an array the same length as {@code closes}; {@code null} at every index below
     *         {@code period}, where the index is undefined.
     */
    public static Double[] calculate(double[] closes, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive, got " + period);
        }

        Double[] result = new Double[closes.length];
        if (closes.length <= period) {
            return result;
        }

        double gainSum = 0;
        double lossSum = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            gainSum += Math.max(0, change);
            lossSum += Math.max(0, -change);
        }

        double averageGain = gainSum / period;
        double averageLoss = lossSum / period;
        result[period] = fromAverages(averageGain, averageLoss);

        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            averageGain = (averageGain * (period - 1) + Math.max(0, change)) / period;
            averageLoss = (averageLoss * (period - 1) + Math.max(0, -change)) / period;
            result[i] = fromAverages(averageGain, averageLoss);
        }

        return result;
    }

    private static double fromAverages(double averageGain, double averageLoss) {
        // A perfectly flat window gives 0/0, which is undefined. Folding it into the
        // averageLoss == 0 branch below would paint a stalled market at the top of the band,
        // reading as extremely overbought when there is no directional pressure at all.
        if (averageGain == 0 && averageLoss == 0) {
            return 50.0;
        }
        if (averageLoss == 0) {
            return 100.0;
        }
        double relativeStrength = averageGain / averageLoss;
        return 100 - 100 / (1 + relativeStrength);
    }
}
