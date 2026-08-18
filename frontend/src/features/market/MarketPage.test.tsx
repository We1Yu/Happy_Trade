import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ChartData, TickerData } from './types';

// lightweight-charts draws on a real canvas, which jsdom does not provide. The chart wiring is
// verified by the type check and by running the app; here we only need MarketPage to mount.
vi.mock('lightweight-charts', () => {
  const makeSeries = () => ({
    setData: vi.fn(),
    applyOptions: vi.fn(),
    createPriceLine: vi.fn(),
  });
  return {
    createChart: vi.fn(() => ({
      addSeries: vi.fn(() => makeSeries()),
      panes: vi.fn(() => []),
      remove: vi.fn(),
    })),
    CandlestickSeries: 'CandlestickSeries',
    HistogramSeries: 'HistogramSeries',
    LineSeries: 'LineSeries',
  };
});

vi.mock('./api/marketApi', async () => {
  const actual = await vi.importActual<typeof import('./api/marketApi')>('./api/marketApi');
  return { ...actual, fetchTicker: vi.fn(), fetchChart: vi.fn() };
});

const { MarketApiError, fetchChart, fetchTicker } = await import('./api/marketApi');
const { MarketPage } = await import('./MarketPage');

const ticker: TickerData = {
  symbol: 'BTCUSDT',
  price: 65432.1,
  changePercent24h: 1.25,
  high24h: 66000,
  low24h: 64000,
  volume24h: 12345.678,
  timestamp: '2026-08-18T07:00:00Z',
};

const chart: ChartData = {
  symbol: 'BTCUSDT',
  interval: '1h',
  candles: [{ time: 100, open: 1, high: 2, low: 0, close: 1.5, volume: 10 }],
  indicators: {
    sma200: [null],
    ema15: [null],
    ema30: [null],
    ema45: [null],
    ema60: [null],
    rsi14: [null],
    macd: { macd: [null], signal: [null], histogram: [null] },
  },
};

beforeEach(() => {
  vi.mocked(fetchTicker).mockReset().mockResolvedValue(ticker);
  vi.mocked(fetchChart).mockReset().mockResolvedValue(chart);
});

afterEach(cleanup);

describe('MarketPage', () => {
  it('shows the live price and loads the 1h chart by default', async () => {
    render(<MarketPage />);

    await waitFor(() => expect(screen.getByText('$65,432.10')).toBeTruthy());
    expect(fetchChart).toHaveBeenCalledWith('BTCUSDT', '1h');
    expect(screen.getByRole('button', { name: '1h' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('reloads the chart at the interval the user picks', async () => {
    render(<MarketPage />);
    await waitFor(() => expect(fetchChart).toHaveBeenCalled());

    screen.getByRole('button', { name: '4h' }).click();

    await waitFor(() => expect(fetchChart).toHaveBeenCalledWith('BTCUSDT', '4h'));
    expect(screen.getByRole('button', { name: '4h' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('surfaces a blocked upstream as a hard error', async () => {
    vi.mocked(fetchTicker).mockRejectedValue(
      new MarketApiError({ code: 'UPSTREAM_BLOCKED', message: 'blocked' }),
    );

    render(<MarketPage />);

    await waitFor(() => expect(screen.getByText('此網路無法連線交易所')).toBeTruthy());
  });

  it('keeps indicator toggles wired to the chart', async () => {
    render(<MarketPage />);
    await waitFor(() => expect(fetchChart).toHaveBeenCalled());

    const rsi = screen.getByLabelText('RSI 14') as HTMLInputElement;
    expect(rsi.checked).toBe(true);

    rsi.click();

    await waitFor(() => expect((screen.getByLabelText('RSI 14') as HTMLInputElement).checked).toBe(false));
  });
});
