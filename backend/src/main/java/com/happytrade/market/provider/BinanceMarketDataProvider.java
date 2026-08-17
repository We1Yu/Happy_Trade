package com.happytrade.market.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reads public market data from Binance.
 *
 * <p><b>Safety:</b> this client calls public endpoints only. It sends no API key and performs no
 * request signing, so it is structurally incapable of placing an order. Adding credentials here
 * would be a red-line change requiring its own ADR.
 */
@Component
public class BinanceMarketDataProvider implements MarketDataProvider {

    private static final int DEFAULT_RETRY_AFTER_SECONDS = 60;

    private final RestClient restClient;

    public BinanceMarketDataProvider(RestClient binanceRestClient) {
        this.restClient = binanceRestClient;
    }

    @Override
    public List<Candle> fetchCandles(String symbol, Interval interval, int limit) {
        JsonNode body = get(uriBuilder -> uriBuilder
                .path("/api/v3/klines")
                .queryParam("symbol", symbol)
                .queryParam("interval", interval.code())
                .queryParam("limit", limit)
                .build());

        List<Candle> candles = new ArrayList<>();
        for (JsonNode row : body) {
            candles.add(new Candle(
                    row.get(0).asLong() / 1000,
                    row.get(1).asDouble(),
                    row.get(2).asDouble(),
                    row.get(3).asDouble(),
                    row.get(4).asDouble(),
                    row.get(5).asDouble()
            ));
        }
        return candles;
    }

    @Override
    public Ticker fetchTicker(String symbol) {
        JsonNode body = get(uriBuilder -> uriBuilder
                .path("/api/v3/ticker/24hr")
                .queryParam("symbol", symbol)
                .build());

        return new Ticker(
                body.get("symbol").asText(),
                body.get("lastPrice").asDouble(),
                body.get("priceChangePercent").asDouble(),
                body.get("highPrice").asDouble(),
                body.get("lowPrice").asDouble(),
                body.get("volume").asDouble(),
                Instant.now()
        );
    }

    private JsonNode get(Function<UriBuilder, URI> uriFunction) {
        try {
            return restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .onStatus(status -> status.value() == 429 || status.value() == 418,
                            (request, response) -> {
                                throw new UpstreamException.RateLimited(
                                        retryAfterSeconds(response.getHeaders().getFirst("Retry-After")));
                            })
                    .onStatus(status -> status.value() == 451,
                            (request, response) -> {
                                throw new UpstreamException.Blocked();
                            })
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new UpstreamException.Timeout(e);
        }
    }

    private static int retryAfterSeconds(String headerValue) {
        if (headerValue == null) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
        try {
            return Integer.parseInt(headerValue.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
    }
}
