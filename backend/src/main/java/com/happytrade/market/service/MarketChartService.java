package com.happytrade.market.service;

import com.happytrade.market.config.CacheConfig;
import com.happytrade.market.indicator.Ema;
import com.happytrade.market.indicator.Macd;
import com.happytrade.market.indicator.MacdResult;
import com.happytrade.market.indicator.Rsi;
import com.happytrade.market.indicator.Sma;
import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.provider.MarketDataProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the chart payload: fetch with warm-up, compute indicators over the full window, then trim
 * the warm-up away so the caller sees only the candles it asked for.
 */
@Service
public class MarketChartService {

    /**
     * Extra candles fetched ahead of the display window. Set by SMA200, the longest lookback in
     * use. If an indicator with a longer lookback is added, this must grow and the {@code limit}
     * ceiling must shrink to keep {@code limit + WARM_UP <= 1000} (the Binance per-request cap).
     */
    public static final int WARM_UP = 200;

    private final MarketDataProvider provider;

    public MarketChartService(MarketDataProvider provider) {
        this.provider = provider;
    }

    public record IndicatorSeries(
            Double[] sma200,
            Double[] ema15,
            Double[] ema30,
            Double[] ema45,
            Double[] ema60,
            Double[] rsi14,
            Double[] macd,
            Double[] macdSignal,
            Double[] macdHistogram
    ) {
    }

    public record ChartData(
            String symbol,
            String interval,
            List<Candle> candles,
            IndicatorSeries indicators
    ) {
    }

    @Cacheable(cacheNames = CacheConfig.CHART_CACHE, key = "#symbol + ':' + #interval + ':' + #limit")
    public ChartData buildChart(String symbol, Interval interval, int limit) {
        List<Candle> fetched = provider.fetchCandles(symbol, interval, limit + WARM_UP);

        double[] closes = fetched.stream().mapToDouble(Candle::close).toArray();

        Double[] sma200 = Sma.calculate(closes, 200);
        Double[] ema15 = Ema.calculate(closes, 15);
        Double[] ema30 = Ema.calculate(closes, 30);
        Double[] ema45 = Ema.calculate(closes, 45);
        Double[] ema60 = Ema.calculate(closes, 60);
        Double[] rsi14 = Rsi.calculate(closes, 14);
        MacdResult macd = Macd.calculate(closes, 12, 26, 9);

        int drop = Math.max(0, Math.min(WARM_UP, fetched.size() - limit));

        return new ChartData(
                symbol,
                interval.code(),
                List.copyOf(fetched.subList(drop, fetched.size())),
                new IndicatorSeries(
                        trim(sma200, drop),
                        trim(ema15, drop),
                        trim(ema30, drop),
                        trim(ema45, drop),
                        trim(ema60, drop),
                        trim(rsi14, drop),
                        trim(macd.macd(), drop),
                        trim(macd.signal(), drop),
                        trim(macd.histogram(), drop)
                )
        );
    }

    @Cacheable(cacheNames = CacheConfig.TICKER_CACHE, key = "#symbol")
    public Ticker fetchTicker(String symbol) {
        return provider.fetchTicker(symbol);
    }

    private static Double[] trim(Double[] series, int drop) {
        return Arrays.copyOfRange(series, drop, series.length);
    }
}
