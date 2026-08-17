package com.happytrade.market.indicator;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MacdTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    private static double[] risingCloses(int length) {
        double[] closes = new double[length];
        for (int i = 0; i < length; i++) {
            closes[i] = 100 + i;
        }
        return closes;
    }

    @Test
    void allThreeSeriesMatchTheInputLength() {
        double[] closes = risingCloses(60);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        assertThat(result.macd()).hasSize(60);
        assertThat(result.signal()).hasSize(60);
        assertThat(result.histogram()).hasSize(60);
    }

    @Test
    void macdIsUndefinedUntilTheSlowEmaExists() {
        double[] closes = risingCloses(60);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        assertThat(result.macd()[24]).isNull();
        assertThat(result.macd()[25]).isNotNull();
    }

    @Test
    void signalIsMappedBackOntoOriginalCandleIndices() {
        double[] closes = risingCloses(60);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        // MACD starts at index 25; the 9-period signal seed consumes 9 MACD values,
        // so the first signal value belongs at index 25 + 9 - 1 = 33.
        assertThat(result.signal()[32]).isNull();
        assertThat(result.signal()[33]).isNotNull();
    }

    @Test
    void histogramIsMacdMinusSignalWhereverBothExist() {
        double[] closes = risingCloses(60);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        for (int i = 0; i < closes.length; i++) {
            if (result.macd()[i] != null && result.signal()[i] != null) {
                assertThat(result.histogram()[i])
                        .isCloseTo(result.macd()[i] - result.signal()[i], TOLERANCE);
            } else {
                assertThat(result.histogram()[i]).isNull();
            }
        }
    }

    @Test
    void returnsAllNullSeriesWhenThereIsNotEnoughData() {
        double[] closes = risingCloses(10);

        MacdResult result = Macd.calculate(closes, 12, 26, 9);

        assertThat(result.macd()).hasSize(10).containsOnlyNulls();
        assertThat(result.signal()).hasSize(10).containsOnlyNulls();
        assertThat(result.histogram()).hasSize(10).containsOnlyNulls();
    }
}
