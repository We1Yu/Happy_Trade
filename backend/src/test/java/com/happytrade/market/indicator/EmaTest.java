package com.happytrade.market.indicator;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmaTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    @Test
    void seedsWithSimpleMovingAverageThenSmooths() {
        double[] values = {1, 2, 3, 4, 5};

        Double[] result = Ema.calculate(values, 3);

        assertThat(result).hasSize(5);
        assertThat(result[0]).isNull();
        assertThat(result[1]).isNull();
        assertThat(result[2]).isCloseTo(2.0, TOLERANCE);
        assertThat(result[3]).isCloseTo(3.0, TOLERANCE);
        assertThat(result[4]).isCloseTo(4.0, TOLERANCE);
    }

    @Test
    void producesExactlyOneValueWhenDataLengthEqualsPeriod() {
        double[] values = {10, 20, 30};

        Double[] result = Ema.calculate(values, 3);

        assertThat(result[0]).isNull();
        assertThat(result[1]).isNull();
        assertThat(result[2]).isCloseTo(20.0, TOLERANCE);
    }

    @Test
    void returnsAllNullWhenThereIsLessDataThanThePeriod() {
        double[] values = {1, 2};

        Double[] result = Ema.calculate(values, 5);

        assertThat(result).hasSize(2).containsOnlyNulls();
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThatThrownBy(() -> Ema.calculate(new double[]{1, 2, 3}, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
