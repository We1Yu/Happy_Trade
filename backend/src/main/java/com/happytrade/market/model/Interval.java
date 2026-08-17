package com.happytrade.market.model;

/** Candle intervals supported by this slice, with their Binance wire codes. */
public enum Interval {

    ONE_MINUTE("1m"),
    FIVE_MINUTES("5m"),
    FIFTEEN_MINUTES("15m"),
    ONE_HOUR("1h"),
    FOUR_HOURS("4h"),
    ONE_DAY("1d");

    private final String code;

    Interval(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Interval fromCode(String code) {
        for (Interval interval : values()) {
            if (interval.code.equals(code)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("Unsupported interval: " + code);
    }
}
