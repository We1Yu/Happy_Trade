# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Initial project structure and governance guidelines.
- Base ADR setup and architecture baseline.
- Backend Spring Boot skeleton (`backend/`) with health-check smoke test, Maven wrapper, and Dockerfile. No JPA/datasource dependency in this slice.
- Dependency-free indicator package: SMA, EMA, RSI (Wilder), and MACD, with the signal line mapped back onto original candle indices.
- SMA and EMA indicators (`Sma`, `Ema`) with trailing-mean and SMA-seeded exponential smoothing, each null-padded during the warm-up window.
- Domain model types for market data: `Candle` (OHLCV record), `Ticker` (price and 24h stats), and `Interval` enum (timeframe codes with bidirectional conversion).
- Keyless Binance market data provider (`BinanceMarketDataProvider`) reading public klines and 24hr ticker endpoints only, with `UpstreamException` mapping for rate-limit (429/418), region-block (451), and timeout responses. Sends no API key and performs no request signing.
- RSI flat-window handling: perfectly flat price series (0/0 RS) now returns 50 (neutral) instead of incorrectly collapsing into the all-gains case.
- Market chart service (`MarketChartService`) that fetches a 200-candle warm-up window on top of the requested display limit, computes SMA200/EMA15-30-45-60/RSI14/MACD over the full window, then trims the warm-up so every indicator series is index-aligned with the returned candles. Falls back to keeping all candles when upstream returns fewer than the display limit.
- Read-only market REST endpoints: `GET /api/market/ticker` and `GET /api/market/chart` (`MarketController`), with a `ChartResponse` DTO that nests the three MACD series, symbol/interval/limit validation (limit 50-800), and a `@RestControllerAdvice` mapping invalid parameters to 400 and upstream rate-limit/block/timeout failures to 503/503/504 with a structured `ApiError` payload. No endpoint places or simulates an order.
