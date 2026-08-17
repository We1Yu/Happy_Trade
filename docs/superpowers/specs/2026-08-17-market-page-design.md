# Market Page Design

* Date: 2026-08-17
* Status: approved
* Scope: First vertical slice of Happy_Trade — a read-only BTC market page, plus the minimum
  runnable skeleton needed to serve it.

## 1. Goal

Deliver a working BTC market page that shows live price, candlestick history, volume, and a
fixed set of technical indicators. Because the repository currently contains no code, this
slice also brings up the minimum Docker Compose skeleton (`db`, `backend`, `frontend`) declared
in ADR-0001.

### Success criteria

1. `docker compose up` brings up all three services.
2. `GET /api/market/ticker?symbol=BTCUSDT` returns live price and 24h statistics.
3. `GET /api/market/chart?symbol=BTCUSDT&interval=1h&limit=500` returns 500 candles plus
   index-aligned indicator series.
4. `http://localhost:5173` renders a four-pane chart (price, volume, RSI, MACD) that refreshes
   without a manual page reload.
5. Indicator unit tests pass against a fixed dataset with known expected values.

## 2. Safety Constraint

The project's hard rule is **no direct automatic order execution**. This slice enforces it
structurally rather than by convention:

* The Binance client calls **public market-data endpoints only**.
* It carries **no API key and performs no request signing**.

Without credentials, order placement is not merely disallowed — it is impossible. Any future
change that introduces a Binance API key must be treated as a red-line change requiring its own
ADR and explicit review.

## 3. Architecture

Three Docker Compose services, consistent with ADR-0001:

| Service | Image / Stack | Port | Role in this slice |
|---|---|---|---|
| `db` | postgres:16 | 5432 | Started, but **not connected to by the backend**. No market data is persisted in this slice. Present so the baseline matches ADR-0001 and the AI-signal slice can adopt it without re-provisioning. |
| `backend` | Java 21 + Spring Boot | 8080 | Fetches from Binance, computes indicators, serves REST. |
| `frontend` | Node + Vite dev server | 5173 | React + TypeScript SPA. Proxies `/api` to the backend. |

The backend does **not** declare a JPA or datasource dependency in this slice. Adding
`spring-boot-starter-data-jpa` before there is data to persist would force datasource
configuration that nothing uses and would make the backend fail to start when `db` is slow to
become healthy.

### Data flow

```
Browser (5173)
   |  GET /api/market/ticker   every 5s
   |  GET /api/market/chart    interval-dependent
   v
Spring Boot (8080)
   |  Caffeine cache lookup (miss -> upstream call)
   v
Binance public REST API
```

## 4. Backend Design

Root package: `com.happytrade.market`.

| Package | Responsibility | Depends on |
|---|---|---|
| `model` | `Candle`, `Ticker`, `Interval` — Java records and one enum. | — |
| `provider` | `MarketDataProvider` interface and `BinanceMarketDataProvider`. Owns all knowledge of Binance's wire format. | `model` |
| `indicator` | `Sma`, `Ema`, `Rsi`, `Macd`. **Pure functions, no Spring dependencies.** | — |
| `service` | `MarketChartService` — orchestrates fetch, warm-up, computation, trimming. | `provider`, `indicator`, `model` |
| `web` | `MarketController` and response DTOs. | `service` |

`indicator` deliberately depends on nothing. Each function takes `double[]` and returns
`Double[]`, so it can be unit-tested directly against a fixed dataset. The AI-signal slice will
reuse this package unchanged, which is why it must not acquire framework dependencies.

### 4.1 Model

```java
public record Candle(
    long time,        // Unix seconds, candle open time
    double open,
    double high,
    double low,
    double close,
    double volume
) {}

public record Ticker(
    String symbol,
    double price,
    double changePercent24h,
    double high24h,
    double low24h,
    double volume24h,
    Instant timestamp
) {}

public enum Interval {
    ONE_MINUTE("1m"), FIVE_MINUTES("5m"), FIFTEEN_MINUTES("15m"),
    ONE_HOUR("1h"), FOUR_HOURS("4h"), ONE_DAY("1d");
}
```

`time` is Unix **seconds** because that is lightweight-charts' native time format. Converting
at the boundary means the frontend performs zero time arithmetic.

### 4.2 Provider

```java
public interface MarketDataProvider {
    List<Candle> fetchCandles(String symbol, Interval interval, int limit);
    Ticker fetchTicker(String symbol);
}
```

`BinanceMarketDataProvider` calls:

* Candles: `GET https://api.binance.com/api/v3/klines?symbol={symbol}&interval={interval}&limit={limit}`
  Response is an array of arrays; index 0 is open time in milliseconds, 1–4 are OHLC as strings,
  5 is base-asset volume as a string.
* Ticker: `GET https://api.binance.com/api/v3/ticker/24hr?symbol={symbol}`
  Response fields used: `lastPrice`, `priceChangePercent`, `highPrice`, `lowPrice`, `volume`.

Connect and read timeouts are 5 seconds each.

### 4.3 Indicator Definitions

Every function returns an array the same length as its input. Positions where the indicator is
mathematically undefined contain `null`. These definitions are normative — the unit tests assert
against them.

**SMA(n)** — arithmetic mean of the trailing `n` closes. `null` for index `< n-1`.

**EMA(n)** — seeded with `SMA(n)` at index `n-1`, then
`EMA_i = close_i * k + EMA_{i-1} * (1 - k)` where `k = 2 / (n + 1)`. `null` for index `< n-1`.

Seeding with an SMA rather than the first close is what makes the series reproducible; starting
from a single close makes early values depend on how much history happened to be fetched.

**RSI(14)** — Wilder's smoothing. Gains and losses are computed per step as
`gain_i = max(0, close_i - close_{i-1})` and `loss_i = max(0, close_{i-1} - close_i)`. The first
`avgGain` and `avgLoss` are the simple means of the first 14 gains and the first 14 losses
respectively, producing the first RSI value at index 14. Thereafter each average is smoothed
independently against its own series:

```
avgGain_i = (avgGain_{i-1} * 13 + gain_i) / 14
avgLoss_i = (avgLoss_{i-1} * 13 + loss_i) / 14
```

Then `RSI = 100 - 100 / (1 + avgGain/avgLoss)`, with two guards on the degenerate cases, checked
in this order:

* `avgGain == 0 && avgLoss == 0` — a perfectly flat window, where `RS` is `0/0` and undefined.
  RSI is **50**. Wilder's original definition is silent here, and folding this case into the
  `avgLoss == 0` branch would paint a stalled or illiquid market at the top of the band, reading
  as extremely overbought when there is no directional pressure at all.
* `avgLoss == 0` (and therefore `avgGain > 0`) — a strictly rising window. RSI is **100**.

`null` for index `< 14`.

**MACD(12, 26, 9)** —
`macd = EMA(12) - EMA(26)`, `null` wherever either input is `null`;
`signal = EMA(9)` computed over the **compacted** non-null MACD values, seeded with the SMA of
its first 9 values; `histogram = macd - signal`, `null` wherever either is `null`.

Because the signal line is computed over a compacted series, its results must be **mapped back
to the original candle indices** before being returned — `signal[i]` must correspond to
`candles[i]`, not to the i-th non-null MACD value. Getting this wrong shifts the whole signal
line left and produces crossovers that never happened, so it is covered explicitly by the
alignment test.

### 4.4 Warm-up and Alignment

Indicators need history that precedes the displayed window. Without it, the left edge of the
chart shows gaps where SMA200 has not yet accumulated enough data.

```
requested limit           = 500
WARM_UP                   = 200          (driven by SMA200, the longest lookback)
fetch limit               = 700          (Binance per-request cap is 1000)
compute indicators over   = 700 candles
drop leading              = 200
return                    = 500 candles + 500-element indicator arrays
```

`WARM_UP = 200` is set by SMA200. Every other configured indicator has a shorter lookback
(EMA60 needs 60, MACD roughly 35, RSI 14), so a single constant covers all of them. If an
indicator with a longer lookback is added later, `WARM_UP` must increase and the `limit` ceiling
must decrease to keep `limit + WARM_UP <= 1000`.

**Alignment contract:** every indicator array has length exactly equal to the returned `candles`
array, and index `i` of any indicator corresponds to `candles[i]`. The frontend applies no
offset. This is asserted by a dedicated test.

If Binance returns fewer candles than requested (a young symbol, or a gap), the service consumes
whatever warm-up is available rather than sacrificing displayed candles:

```
drop = max(0, min(WARM_UP, received - limit))
```

With `received = 700, limit = 500` this drops the full 200 warm-up candles. With
`received = 100, limit = 500` it drops nothing and returns all 100 — displaying fewer candles
with partially `null` indicators beats displaying almost none. Indicators that cannot be
computed simply stay `null`.

### 4.5 Caching

Caffeine, in-memory, keyed by `(symbol, interval)` for charts and by `symbol` for tickers.

| Cache | TTL | Reason |
|---|---|---|
| chart | 15s | Several browser tabs polling at once must not multiply upstream calls. |
| ticker | 3s | Below the 5s frontend poll, so a refresh nearly always sees fresh data. |

### 4.6 Error Handling

| Upstream condition | Backend response | Frontend behaviour |
|---|---|---|
| Binance 429 / 418 (rate limited) | 503 with `retryAfter` seconds | Back off, keep last good data, show "資料延遲" badge |
| Binance 451 (geo-blocked) | 503 with an explicit blocked-region message | Show a terminal error, stop retrying |
| Timeout (>5s) | 504 | Same as rate limited |
| Invalid `interval` or `limit` | 400 with the reason | Treated as a programming error; not user-recoverable |

**Core principle: no error clears the chart.** The frontend always retains the last successful
payload and layers a status indicator over it. While watching a market, a blank screen is far
worse than data that is 30 seconds stale.

## 5. API Contract

### `GET /api/market/ticker`

Query parameters: `symbol` (default `BTCUSDT`).

`timestamp` is the **server's observation time** — the moment the backend received the upstream
response — not a Binance-supplied field. It exists so the frontend can display how stale the
displayed price is when polling is backing off after an error.

```json
{
  "symbol": "BTCUSDT",
  "price": 64312.50,
  "changePercent24h": 2.41,
  "high24h": 65100.00,
  "low24h": 62800.00,
  "volume24h": 41235.6,
  "timestamp": "2026-08-17T14:23:05Z"
}
```

### `GET /api/market/chart`

Query parameters:

| Name | Default | Constraint |
|---|---|---|
| `symbol` | `BTCUSDT` | Non-empty, uppercase alphanumeric |
| `interval` | `1h` | One of `1m`, `5m`, `15m`, `1h`, `4h`, `1d` |
| `limit` | `500` | 50–800 (800 + 200 warm-up = the 1000 upstream cap) |

```json
{
  "symbol": "BTCUSDT",
  "interval": "1h",
  "candles": [
    {
      "time": 1755440000,
      "open": 63980.1,
      "high": 64420.0,
      "low": 63910.5,
      "close": 64312.5,
      "volume": 812.34
    }
  ],
  "indicators": {
    "sma200": [64010.2],
    "ema15": [64288.1],
    "ema30": [64201.7],
    "ema45": [64150.3],
    "ema60": [64098.8],
    "rsi14": [61.7],
    "macd": {
      "macd": [128.4],
      "signal": [96.2],
      "histogram": [32.2]
    }
  }
}
```

## 6. Frontend Design

```
src/features/market/
  MarketPage.tsx            Layout and state ownership
  types.ts                  Mirrors the backend DTOs
  api/marketApi.ts          Typed fetch wrappers
  hooks/useTicker.ts        5s polling
  hooks/useChartData.ts     Interval-dependent polling
  components/
    PriceHeader.tsx         Price, 24h change, high/low/volume
    IntervalSelector.tsx    Interval buttons
    PriceChart.tsx          lightweight-charts, four panes
    IndicatorToggles.tsx    Per-indicator visibility switches
```

### 6.1 Layout

Four vertically stacked panes sharing one time axis, so the crosshair and zoom move together:

| Pane | Content | Height |
|---|---|---|
| Price | Candlesticks + SMA200 + EMA15/30/45/60 | 60% |
| Volume | Volume histogram | 15% |
| RSI | RSI(14) with 30/70 reference lines | 12% |
| MACD | MACD line, signal line, histogram | 13% |

Sharing an axis is the point of this layout: it makes "price broke out, and here is where RSI
and MACD were at that moment" readable in one glance.

### 6.2 Charting

lightweight-charts v5, using its Panes API — one chart instance with four panes, which gives
axis and crosshair synchronisation for free.

**Fallback:** if the v5 Panes API proves unworkable, fall back to the v4-era approach — four
separate chart instances with their `timeScale` ranges manually synchronised via
`subscribeVisibleLogicalRangeChange`. This costs more code and needs care to avoid feedback
loops between the synchronised charts, so it is the second choice, not the first.

### 6.3 Polling

Poll intervals scale with the candle interval; re-fetching daily candles every 5 seconds is
pointless load.

| Interval | 1m | 5m | 15m | 1h | 4h | 1d |
|---|---|---|---|---|---|---|
| Chart poll | 20s | 30s | 60s | 60s | 120s | 300s |

The ticker polls every 5s regardless of the selected interval.

Polling pauses when `document.visibilityState` is `hidden` and performs one immediate fetch on
becoming visible again, so a backgrounded tab neither wastes upstream quota nor shows stale data
when the user returns.

## 7. Testing

| Target | Method |
|---|---|
| Indicators | JUnit against a fixed dataset with pre-computed expected values. Includes boundaries: fewer candles than the period yields all `null`; exactly the period yields the first value at the correct index. |
| Provider | MockWebServer replaying captured Binance JSON. Verifies parsing and the error-mapping table in §4.6. |
| Controller | `@WebMvcTest` for parameter validation and status codes. |
| Alignment contract | A dedicated test asserting every indicator array length equals the candle array length. |
| End to end | `docker compose up`, `curl` both endpoints, then open `localhost:5173` and confirm the four panes render and refresh. |

The indicator tests carry the most weight. They are the only place where a silently wrong number
would otherwise reach the chart looking entirely plausible.

## 8. Out of Scope (YAGNI)

Deliberately excluded from this slice:

* Multi-symbol switching UI — `symbol` is a parameter, but the UI is fixed to BTCUSDT.
* Persisting candles to Postgres — deferred until the AI-signal slice defines what granularity
  and retention it actually needs.
* Drawing tools.
* User-configurable indicator periods — periods are fixed at SMA200, EMA15/30/45/60, RSI14,
  MACD(12,26,9).
* WebSocket / SSE streaming — the provider interface is shaped to allow it later, but this slice
  polls.

## 9. Governance

* **ADR-0002 — Market data source and charting stack.** Required on two counts from
  `docs/adr/README.md`: introducing an external API (Binance) and introducing third-party
  libraries (lightweight-charts, Caffeine). Combined into one ADR because the two decisions are
  coupled: choosing lightweight-charts means the backend must own the data, which is what forces
  a real upstream data source. The ADR must also record how the keyless client design supports
  the no-auto-trading red line.
* **CHANGELOG** — add entries under `[Unreleased] / Added`.
