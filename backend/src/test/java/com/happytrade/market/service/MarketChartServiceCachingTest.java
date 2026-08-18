package com.happytrade.market.service;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.provider.MarketDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MarketChartServiceCachingTest {

    static final AtomicInteger candleCalls = new AtomicInteger();
    static final AtomicInteger tickerCalls = new AtomicInteger();

    @TestConfiguration
    static class CountingProviderConfig {

        @Bean
        @Primary
        MarketDataProvider countingProvider() {
            return new MarketDataProvider() {
                @Override
                public List<Candle> fetchCandles(String symbol, Interval interval, int limit) {
                    candleCalls.incrementAndGet();
                    List<Candle> candles = new ArrayList<>();
                    for (int i = 0; i < 700; i++) {
                        candles.add(new Candle(1_700_000_000L + i * 3600L, 100, 101, 99, 100 + i, 10));
                    }
                    return candles;
                }

                @Override
                public Ticker fetchTicker(String symbol) {
                    tickerCalls.incrementAndGet();
                    return new Ticker(symbol, 1, 2, 3, 4, 5, Instant.EPOCH);
                }
            };
        }
    }

    @Autowired
    private MarketChartService service;

    @Test
    void identicalChartRequestsHitTheUpstreamOnlyOnce() {
        candleCalls.set(0);

        service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);
        service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        assertThat(candleCalls.get()).isEqualTo(1);
    }

    @Test
    void differentIntervalsAreCachedSeparately() {
        candleCalls.set(0);

        service.buildChart("ETHUSDT", Interval.ONE_MINUTE, 500);
        service.buildChart("ETHUSDT", Interval.FOUR_HOURS, 500);

        assertThat(candleCalls.get()).isEqualTo(2);
    }

    @Test
    void identicalTickerRequestsHitTheUpstreamOnlyOnce() {
        tickerCalls.set(0);

        service.fetchTicker("XRPUSDT");
        service.fetchTicker("XRPUSDT");

        assertThat(tickerCalls.get()).isEqualTo(1);
    }
}
