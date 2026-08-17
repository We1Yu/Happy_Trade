package com.happytrade.market.indicator;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RsiTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    @Test
    void firstValueLandsExactlyAtIndexPeriod() {
        double[] closes = new double[20];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 + i;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result).hasSize(20);
        assertThat(result[13]).isNull();
        assertThat(result[14]).isNotNull();
    }

    @Test
    void strictlyRisingSeriesGivesOneHundred() {
        double[] closes = new double[20];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 + i;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result[14]).isCloseTo(100.0, TOLERANCE);
        assertThat(result[19]).isCloseTo(100.0, TOLERANCE);
    }

    @Test
    void strictlyFallingSeriesGivesZero() {
        double[] closes = new double[20];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 - i;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result[14]).isCloseTo(0.0, TOLERANCE);
        assertThat(result[19]).isCloseTo(0.0, TOLERANCE);
    }

    @Test
    void alternatingEqualMovesConvergeToWildersSteadyState() {
        // A +1/-1 alternating series does NOT settle at 50 under Wilder's smoothing: the two
        // averages oscillate in antiphase rather than staying equal. Solving the fixed point for
        // period 14 gives, immediately after a rise, averageGain = 14/27 and averageLoss = 13/27
        // (and mirrored immediately after a fall). So RSI alternates between the two values below.
        double afterRise = 100 - 1300.0 / 27;
        double afterFall = 100 - 1400.0 / 27;

        double[] closes = new double[401];
        closes[0] = 100;
        for (int i = 1; i < closes.length; i++) {
            closes[i] = (i % 2 == 1) ? 101 : 100;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result[399]).isCloseTo(afterRise, Offset.offset(1e-6));
        assertThat(result[400]).isCloseTo(afterFall, Offset.offset(1e-6));
    }

    @Test
    void returnsAllNullWhenDataIsNotLongerThanThePeriod() {
        double[] closes = new double[14];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 + i;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result).hasSize(14).containsOnlyNulls();
    }

    @Test
    void flatSeriesGivesFifty() {
        double[] closes = new double[20];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100.0;
        }

        Double[] result = Rsi.calculate(closes, 14);

        assertThat(result[14]).isCloseTo(50.0, TOLERANCE);
        assertThat(result[19]).isCloseTo(50.0, TOLERANCE);
    }
}
