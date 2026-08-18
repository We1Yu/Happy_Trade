package com.happytrade.market.web;

import com.happytrade.market.model.Candle;
import com.happytrade.market.service.MarketChartService;

import java.util.List;

/**
 * Wire shape of {@code GET /api/market/chart}.
 *
 * <p>MACD is nested one level deeper than the other indicators because its three series belong
 * together; the service keeps them flat internally, and this record does the regrouping.
 */
public record ChartResponse(
        String symbol,
        String interval,
        List<Candle> candles,
        Indicators indicators
) {

    public record MacdSeries(Double[] macd, Double[] signal, Double[] histogram) {
    }

    public record Indicators(
            Double[] sma200,
            Double[] ema15,
            Double[] ema30,
            Double[] ema45,
            Double[] ema60,
            Double[] rsi14,
            MacdSeries macd
    ) {
    }

    public static ChartResponse from(MarketChartService.ChartData data) {
        MarketChartService.IndicatorSeries series = data.indicators();
        return new ChartResponse(
                data.symbol(),
                data.interval(),
                data.candles(),
                new Indicators(
                        series.sma200(),
                        series.ema15(),
                        series.ema30(),
                        series.ema45(),
                        series.ema60(),
                        series.rsi14(),
                        new MacdSeries(series.macd(), series.macdSignal(), series.macdHistogram())
                )
        );
    }
}
