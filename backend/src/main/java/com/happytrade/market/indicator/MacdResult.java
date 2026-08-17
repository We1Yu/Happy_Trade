package com.happytrade.market.indicator;

/**
 * The three MACD series. All three have the same length as the input closes, with {@code null}
 * wherever the value is undefined.
 */
public record MacdResult(Double[] macd, Double[] signal, Double[] histogram) {
}
