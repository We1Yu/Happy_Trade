package com.happytrade.market.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntervalTest {

    @Test
    void fromCodeResolvesEverySupportedInterval() {
        assertThat(Interval.fromCode("1m")).isEqualTo(Interval.ONE_MINUTE);
        assertThat(Interval.fromCode("5m")).isEqualTo(Interval.FIVE_MINUTES);
        assertThat(Interval.fromCode("15m")).isEqualTo(Interval.FIFTEEN_MINUTES);
        assertThat(Interval.fromCode("1h")).isEqualTo(Interval.ONE_HOUR);
        assertThat(Interval.fromCode("4h")).isEqualTo(Interval.FOUR_HOURS);
        assertThat(Interval.fromCode("1d")).isEqualTo(Interval.ONE_DAY);
    }

    @Test
    void codeRoundTripsBackToTheEnum() {
        for (Interval interval : Interval.values()) {
            assertThat(Interval.fromCode(interval.code())).isEqualTo(interval);
        }
    }

    @Test
    void fromCodeRejectsUnsupportedInterval() {
        assertThatThrownBy(() -> Interval.fromCode("3m"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3m");
    }
}
