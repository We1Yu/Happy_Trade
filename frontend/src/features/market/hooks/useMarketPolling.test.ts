import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CHART_POLL_MS, useChartData } from './useChartData';
import { useTicker } from './useTicker';

vi.mock('../api/marketApi', async () => {
  const actual = await vi.importActual<typeof import('../api/marketApi')>('../api/marketApi');
  return {
    ...actual,
    fetchTicker: vi.fn().mockResolvedValue({ symbol: 'BTCUSDT', price: 1 }),
    fetchChart: vi.fn().mockResolvedValue({ symbol: 'BTCUSDT', interval: '1h' }),
  };
});

const { fetchChart, fetchTicker } = await import('../api/marketApi');

async function advance(ms: number) {
  await act(async () => {
    vi.advanceTimersByTime(ms);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.mocked(fetchTicker).mockClear();
  vi.mocked(fetchChart).mockClear();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useTicker', () => {
  it('polls the ticker for the given symbol every 5 seconds', async () => {
    renderHook(() => useTicker('ETHUSDT'));
    await advance(0);
    await advance(10_000);

    expect(fetchTicker).toHaveBeenCalledTimes(3);
    expect(fetchTicker).toHaveBeenLastCalledWith('ETHUSDT');
  });
});

describe('useChartData', () => {
  it('polls the chart at the cadence mapped to the interval', async () => {
    renderHook(() => useChartData('BTCUSDT', '1d'));
    await advance(0);
    expect(fetchChart).toHaveBeenCalledTimes(1);
    expect(fetchChart).toHaveBeenCalledWith('BTCUSDT', '1d');

    await advance(CHART_POLL_MS['1d'] - 1);
    expect(fetchChart).toHaveBeenCalledTimes(1);

    await advance(1);
    expect(fetchChart).toHaveBeenCalledTimes(2);
  });

  it('polls a 1m chart far more often than a 1d chart', () => {
    expect(CHART_POLL_MS['1m']).toBeLessThan(CHART_POLL_MS['1d']);
  });
});
