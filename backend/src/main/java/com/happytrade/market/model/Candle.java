package com.happytrade.market.model;

/**
 * A single OHLCV candle.
 *
 * <p>{@code time} is the candle open time in Unix <b>seconds</b>, which is the native time format
 * of lightweight-charts. Converting at this boundary means the frontend performs no time
 * arithmetic of its own.
 */
public record Candle(
        long time,
        double open,
        double high,
        double low,
        double close,
        double volume
) {
}
