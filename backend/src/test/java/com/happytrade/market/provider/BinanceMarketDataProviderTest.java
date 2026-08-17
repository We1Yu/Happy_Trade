package com.happytrade.market.provider;

import com.happytrade.market.model.Candle;
import com.happytrade.market.model.Interval;
import com.happytrade.market.model.Ticker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinanceMarketDataProviderTest {

    private MockWebServer server;
    private BinanceMarketDataProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        provider = new BinanceMarketDataProvider(restClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void parsesKlinesIntoCandlesWithUnixSecondTimes() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          [1755440000000,"63980.10","64420.00","63910.50","64312.50","812.34",
                           1755443599999,"52000000.00",1200,"400.00","25000000.00","0"],
                          [1755443600000,"64312.50","64500.00","64200.00","64450.00","645.10",
                           1755447199999,"41000000.00",980,"320.00","20000000.00","0"]
                        ]
                        """));

        List<Candle> candles = provider.fetchCandles("BTCUSDT", Interval.ONE_HOUR, 700);

        assertThat(candles).hasSize(2);
        Candle first = candles.get(0);
        assertThat(first.time()).isEqualTo(1755440000L);
        assertThat(first.open()).isEqualTo(63980.10);
        assertThat(first.high()).isEqualTo(64420.00);
        assertThat(first.low()).isEqualTo(63910.50);
        assertThat(first.close()).isEqualTo(64312.50);
        assertThat(first.volume()).isEqualTo(812.34);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath())
                .contains("/api/v3/klines")
                .contains("symbol=BTCUSDT")
                .contains("interval=1h")
                .contains("limit=700");
    }

    @Test
    void sendsNoApiKeyOrSignature() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        provider.fetchCandles("BTCUSDT", Interval.ONE_HOUR, 100);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("X-MBX-APIKEY")).isNull();
        assertThat(request.getPath()).doesNotContain("signature");
    }

    @Test
    void parsesTicker() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "symbol": "BTCUSDT",
                          "lastPrice": "64312.50",
                          "priceChangePercent": "2.41",
                          "highPrice": "65100.00",
                          "lowPrice": "62800.00",
                          "volume": "41235.60"
                        }
                        """));

        Ticker ticker = provider.fetchTicker("BTCUSDT");

        assertThat(ticker.symbol()).isEqualTo("BTCUSDT");
        assertThat(ticker.price()).isEqualTo(64312.50);
        assertThat(ticker.changePercent24h()).isEqualTo(2.41);
        assertThat(ticker.high24h()).isEqualTo(65100.00);
        assertThat(ticker.low24h()).isEqualTo(62800.00);
        assertThat(ticker.volume24h()).isEqualTo(41235.60);
        assertThat(ticker.timestamp()).isNotNull();
    }

    @Test
    void mapsRateLimitToRateLimitedWithRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "30"));

        assertThatThrownBy(() -> provider.fetchTicker("BTCUSDT"))
                .isInstanceOf(UpstreamException.RateLimited.class)
                .satisfies(thrown ->
                        assertThat(((UpstreamException.RateLimited) thrown).retryAfterSeconds()).isEqualTo(30));
    }

    @Test
    void mapsTeapotToRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(418));

        assertThatThrownBy(() -> provider.fetchTicker("BTCUSDT"))
                .isInstanceOf(UpstreamException.RateLimited.class);
    }

    @Test
    void mapsUnavailableForLegalReasonsToBlocked() {
        server.enqueue(new MockResponse().setResponseCode(451));

        assertThatThrownBy(() -> provider.fetchTicker("BTCUSDT"))
                .isInstanceOf(UpstreamException.Blocked.class);
    }
}
