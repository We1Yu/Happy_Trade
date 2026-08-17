package com.happytrade.market.indicator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmaTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void computesTrailingMeanAndNullsTheWarmUpPositions() {
        double[] values = {1, 2, 3, 4, 5};

        Double[] result = Sma.calculate(values, 3);

        assertThat(result).hasSize(5);
        assertThat(result[0]).isNull();
        assertThat(result[1]).isNull();
        assertThat(result[2]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(result[3]).isCloseTo(3.0, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(result[4]).isCloseTo(4.0, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    void producesFirstValueExactlyAtIndexPeriodMinusOne() {
        double[] values = {10, 20, 30};

        Double[] result = Sma.calculate(values, 3);

        assertThat(result[1]).isNull();
        assertThat(result[2]).isCloseTo(20.0, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    void returnsAllNullWhenThereIsLessDataThanThePeriod() {
        double[] values = {1, 2};

        Double[] result = Sma.calculate(values, 5);

        assertThat(result).hasSize(2).containsOnlyNulls();
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThatThrownBy(() -> Sma.calculate(new double[]{1, 2, 3}, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
