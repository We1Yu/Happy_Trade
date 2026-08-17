package com.happytrade.market.indicator;

import java.util.ArrayList;
import java.util.List;

/** Moving Average Convergence Divergence. */
public final class Macd {

    private Macd() {
    }

    public static MacdResult calculate(double[] closes, int fastPeriod, int slowPeriod, int signalPeriod) {
        Double[] fastEma = Ema.calculate(closes, fastPeriod);
        Double[] slowEma = Ema.calculate(closes, slowPeriod);

        Double[] macd = new Double[closes.length];
        for (int i = 0; i < closes.length; i++) {
            if (fastEma[i] != null && slowEma[i] != null) {
                macd[i] = fastEma[i] - slowEma[i];
            }
        }

        Double[] signal = signalMappedToOriginalIndices(macd, signalPeriod);

        Double[] histogram = new Double[closes.length];
        for (int i = 0; i < closes.length; i++) {
            if (macd[i] != null && signal[i] != null) {
                histogram[i] = macd[i] - signal[i];
            }
        }

        return new MacdResult(macd, signal, histogram);
    }

    /**
     * The signal line is an EMA of the MACD series. The MACD series has a {@code null} warm-up
     * prefix, so it is compacted before smoothing and the results are then written back to the
     * indices they came from. Skipping that write-back would shift the signal line left and
     * produce crossovers that never occurred.
     */
    private static Double[] signalMappedToOriginalIndices(Double[] macd, int signalPeriod) {
        List<Integer> sourceIndices = new ArrayList<>();
        List<Double> definedValues = new ArrayList<>();

        for (int i = 0; i < macd.length; i++) {
            if (macd[i] != null) {
                sourceIndices.add(i);
                definedValues.add(macd[i]);
            }
        }

        double[] compacted = new double[definedValues.size()];
        for (int i = 0; i < definedValues.size(); i++) {
            compacted[i] = definedValues.get(i);
        }

        Double[] compactedSignal = Ema.calculate(compacted, signalPeriod);

        Double[] signal = new Double[macd.length];
        for (int i = 0; i < compactedSignal.length; i++) {
            if (compactedSignal[i] != null) {
                signal[sourceIndices.get(i)] = compactedSignal[i];
            }
        }

        return signal;
    }
}
