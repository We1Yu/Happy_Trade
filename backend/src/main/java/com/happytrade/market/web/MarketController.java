package com.happytrade.market.web;

import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.service.MarketChartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only market data endpoints. No endpoint here places or simulates an order. */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private static final int MIN_LIMIT = 50;
    private static final int MAX_LIMIT = 800;

    private final MarketChartService service;

    public MarketController(MarketChartService service) {
        this.service = service;
    }

    @GetMapping("/ticker")
    public Ticker ticker(@RequestParam(defaultValue = "BTCUSDT") String symbol) {
        return service.fetchTicker(validateSymbol(symbol));
    }

    @GetMapping("/chart")
    public ChartResponse chart(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "500") int limit) {

        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between " + MIN_LIMIT + " and " + MAX_LIMIT + ", got " + limit);
        }

        return ChartResponse.from(
                service.buildChart(validateSymbol(symbol), Interval.fromCode(interval), limit));
    }

    private static String validateSymbol(String symbol) {
        if (!symbol.matches("[A-Z0-9]{1,20}")) {
            throw new IllegalArgumentException("Invalid symbol: " + symbol);
        }
        return symbol;
    }
}
