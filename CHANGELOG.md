# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Initial project structure and governance guidelines.
- Base ADR setup and architecture baseline.
- Backend Spring Boot skeleton (`backend/`) with health-check smoke test, Maven wrapper, and Dockerfile. No JPA/datasource dependency in this slice.
- Dependency-free indicator package: SMA, EMA, RSI (Wilder), and MACD, with the signal line mapped back onto original candle indices.
- Domain model types for market data: `Candle` (OHLCV record), `Ticker` (price and 24h stats), and `Interval` enum (timeframe codes with bidirectional conversion).
