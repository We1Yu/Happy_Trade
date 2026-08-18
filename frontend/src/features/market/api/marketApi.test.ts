import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchChart, fetchTicker, MarketApiError } from './marketApi';

function mockFetchOnce(body: unknown, init: { status?: number } = {}) {
  const response = new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: { 'Content-Type': 'application/json' },
  });
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchTicker', () => {
  it('requests the ticker endpoint with the symbol', async () => {
    mockFetchOnce({
      symbol: 'BTCUSDT',
      price: 64312.5,
      changePercent24h: 2.41,
      high24h: 65100,
      low24h: 62800,
      volume24h: 41235.6,
      timestamp: '2026-08-17T14:23:05Z',
    });

    const ticker = await fetchTicker('BTCUSDT');

    expect(ticker.price).toBe(64312.5);
    expect(fetch).toHaveBeenCalledWith('/api/market/ticker?symbol=BTCUSDT');
  });

  it('throws MarketApiError carrying the backend code and retryAfter', async () => {
    mockFetchOnce(
      { code: 'UPSTREAM_RATE_LIMITED', message: 'slow down', retryAfter: 30 },
      { status: 503 },
    );

    await expect(fetchTicker('BTCUSDT')).rejects.toMatchObject({
      code: 'UPSTREAM_RATE_LIMITED',
      retryAfter: 30,
    });
    await expect(fetchTicker('BTCUSDT')).rejects.toBeInstanceOf(MarketApiError);
  });
});

describe('fetchChart', () => {
  it('requests the chart endpoint with symbol, interval, and limit', async () => {
    mockFetchOnce({
      symbol: 'BTCUSDT',
      interval: '1h',
      candles: [],
      indicators: {
        sma200: [],
        ema15: [],
        ema30: [],
        ema45: [],
        ema60: [],
        rsi14: [],
        macd: { macd: [], signal: [], histogram: [] },
      },
    });

    await fetchChart('BTCUSDT', '1h', 500);

    expect(fetch).toHaveBeenCalledWith('/api/market/chart?symbol=BTCUSDT&interval=1h&limit=500');
  });
});
