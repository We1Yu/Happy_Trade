package com.happytrade.market.provider;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;

import java.util.List;

/**
 * Read-only market data access.
 *
 * <p>This interface deliberately exposes no order-placement operation. The project's hard rule is
 * that nothing may place orders automatically.
 */
public interface MarketDataProvider {

    List<Candle> fetchCandles(String symbol, Interval interval, int limit);

    Ticker fetchTicker(String symbol);
}
