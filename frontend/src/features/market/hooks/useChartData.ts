import { useCallback } from 'react';
import { fetchChart } from '../api/marketApi';
import type { ChartData, IntervalCode } from '../types';
import { usePolling, type PollingState } from './usePolling';

/** Poll cadence scales with the candle interval — re-fetching daily candles every five seconds is pointless load. */
export const CHART_POLL_MS: Record<IntervalCode, number> = {
  '1m': 20_000,
  '5m': 30_000,
  '15m': 60_000,
  '1h': 60_000,
  '4h': 120_000,
  '1d': 300_000,
};

export function useChartData(symbol: string, interval: IntervalCode): PollingState<ChartData> {
  const fetcher = useCallback(() => fetchChart(symbol, interval), [symbol, interval]);
  return usePolling(fetcher, CHART_POLL_MS[interval]);
}
