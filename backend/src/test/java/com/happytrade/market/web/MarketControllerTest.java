package com.happytrade.market.web;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import com.happytrade.market.provider.UpstreamException;
import com.happytrade.market.service.MarketChartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketController.class)
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketChartService service;

    private static MarketChartService.ChartData sampleChart() {
        return new MarketChartService.ChartData(
                "BTCUSDT",
                "1h",
                List.of(new Candle(1755440000L, 63980.1, 64420.0, 63910.5, 64312.5, 812.34)),
                new MarketChartService.IndicatorSeries(
                        new Double[]{64010.2},
                        new Double[]{64288.1},
                        new Double[]{64201.7},
                        new Double[]{64150.3},
                        new Double[]{64098.8},
                        new Double[]{61.7},
                        new Double[]{128.4},
                        new Double[]{96.2},
                        new Double[]{32.2}
                )
        );
    }

    @Test
    void returnsTicker() throws Exception {
        given(service.fetchTicker("BTCUSDT")).willReturn(
                new Ticker("BTCUSDT", 64312.50, 2.41, 65100.0, 62800.0, 41235.6,
                        Instant.parse("2026-08-17T14:23:05Z")));

        mockMvc.perform(get("/api/market/ticker").param("symbol", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.price").value(64312.50))
                .andExpect(jsonPath("$.changePercent24h").value(2.41))
                .andExpect(jsonPath("$.high24h").value(65100.0))
                .andExpect(jsonPath("$.low24h").value(62800.0))
                .andExpect(jsonPath("$.volume24h").value(41235.6))
                .andExpect(jsonPath("$.timestamp").value("2026-08-17T14:23:05Z"));
    }

    @Test
    void returnsChartWithNestedIndicatorShape() throws Exception {
        given(service.buildChart(anyString(), any(Interval.class), anyInt())).willReturn(sampleChart());

        mockMvc.perform(get("/api/market/chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.interval").value("1h"))
                .andExpect(jsonPath("$.candles[0].time").value(1755440000L))
                .andExpect(jsonPath("$.candles[0].close").value(64312.5))
                .andExpect(jsonPath("$.indicators.sma200[0]").value(64010.2))
                .andExpect(jsonPath("$.indicators.ema15[0]").value(64288.1))
                .andExpect(jsonPath("$.indicators.rsi14[0]").value(61.7))
                .andExpect(jsonPath("$.indicators.macd.macd[0]").value(128.4))
                .andExpect(jsonPath("$.indicators.macd.signal[0]").value(96.2))
                .andExpect(jsonPath("$.indicators.macd.histogram[0]").value(32.2));
    }

    @Test
    void rejectsUnsupportedInterval() throws Exception {
        mockMvc.perform(get("/api/market/chart").param("interval", "3m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsLimitAboveTheCeiling() throws Exception {
        mockMvc.perform(get("/api/market/chart").param("limit", "801"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejectsLimitBelowTheFloor() throws Exception {
        mockMvc.perform(get("/api/market/chart").param("limit", "49"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void mapsRateLimitedToServiceUnavailableWithRetryAfter() throws Exception {
        given(service.fetchTicker(anyString())).willThrow(new UpstreamException.RateLimited(30));

        mockMvc.perform(get("/api/market/ticker"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfter").value(30));
    }

    @Test
    void mapsBlockedToServiceUnavailable() throws Exception {
        given(service.fetchTicker(anyString())).willThrow(new UpstreamException.Blocked());

        mockMvc.perform(get("/api/market/ticker"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_BLOCKED"));
    }

    @Test
    void mapsTimeoutToGatewayTimeout() throws Exception {
        given(service.fetchTicker(anyString()))
                .willThrow(new UpstreamException.Timeout(new RuntimeException("boom")));

        mockMvc.perform(get("/api/market/ticker"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("UPSTREAM_TIMEOUT"));
    }
}
