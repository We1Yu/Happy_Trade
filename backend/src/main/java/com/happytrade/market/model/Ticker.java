package com.happytrade.market.model;

import java.time.Instant;

/**
 * Latest price plus rolling 24-hour statistics.
 *
 * <p>{@code timestamp} is the server's observation time — the moment the backend received the
 * upstream response — not a value supplied by the exchange. The frontend uses it to show how
 * stale the displayed price is while polling is backing off after an error.
 */
public record Ticker(
        String symbol,
        double price,
        double changePercent24h,
        double high24h,
        double low24h,
        double volume24h,
        Instant timestamp
) {
}
