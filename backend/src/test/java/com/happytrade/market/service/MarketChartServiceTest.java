package com.happytrade.market.service;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.provider.MarketDataProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketChartServiceTest {

    /** Returns exactly {@code available} synthetic candles regardless of what was requested. */
    private static class StubProvider implements MarketDataProvider {

        private final int available;
        int lastRequestedLimit;

        StubProvider(int available) {
            this.available = available;
        }

        @Override
        public List<Candle> fetchCandles(String symbol, Interval interval, int limit) {
            lastRequestedLimit = limit;
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < available; i++) {
                double close = 100 + i;
                candles.add(new Candle(1_700_000_000L + i * 3600L, close, close + 1, close - 1, close, 10 + i));
            }
            return candles;
        }

        @Override
        public Ticker fetchTicker(String symbol) {
            return new Ticker(symbol, 1, 2, 3, 4, 5, Instant.EPOCH);
        }
    }

    @Test
    void requestsWarmUpCandlesOnTopOfTheDisplayLimit() {
        StubProvider provider = new StubProvider(700);
        MarketChartService service = new MarketChartService(provider);

        service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        assertThat(provider.lastRequestedLimit).isEqualTo(700);
    }

    @Test
    void trimsWarmUpAndReturnsExactlyTheRequestedCandles() {
        MarketChartService service = new MarketChartService(new StubProvider(700));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        assertThat(data.candles()).hasSize(500);
    }

    @Test
    void everyIndicatorSeriesHasTheSameLengthAsTheCandles() {
        MarketChartService service = new MarketChartService(new StubProvider(700));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);
        MarketChartService.IndicatorSeries indicators = data.indicators();
        int expected = data.candles().size();

        assertThat(indicators.sma200()).hasSize(expected);
        assertThat(indicators.ema15()).hasSize(expected);
        assertThat(indicators.ema30()).hasSize(expected);
        assertThat(indicators.ema45()).hasSize(expected);
        assertThat(indicators.ema60()).hasSize(expected);
        assertThat(indicators.rsi14()).hasSize(expected);
        assertThat(indicators.macd()).hasSize(expected);
        assertThat(indicators.macdSignal()).hasSize(expected);
        assertThat(indicators.macdHistogram()).hasSize(expected);
    }

    @Test
    void warmUpMeansSma200IsDefinedAtTheVeryFirstReturnedCandle() {
        MarketChartService service = new MarketChartService(new StubProvider(700));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        // 200 warm-up candles were dropped, so SMA200 is already defined at index 0.
        assertThat(data.indicators().sma200()[0]).isNotNull();
    }

    @Test
    void keepsEveryCandleWhenUpstreamReturnsFewerThanTheDisplayLimit() {
        MarketChartService service = new MarketChartService(new StubProvider(100));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.ONE_HOUR, 500);

        // drop = max(0, min(200, 100 - 500)) = 0 — showing 100 candles with partially null
        // indicators beats showing almost none.
        assertThat(data.candles()).hasSize(100);
        assertThat(data.indicators().sma200()).hasSize(100).containsOnlyNulls();
    }

    @Test
    void echoesSymbolAndIntervalCode() {
        MarketChartService service = new MarketChartService(new StubProvider(700));

        MarketChartService.ChartData data = service.buildChart("BTCUSDT", Interval.FOUR_HOURS, 500);

        assertThat(data.symbol()).isEqualTo("BTCUSDT");
        assertThat(data.interval()).isEqualTo("4h");
    }
}
