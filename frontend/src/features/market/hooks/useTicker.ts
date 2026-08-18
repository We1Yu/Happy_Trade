import { useCallback } from 'react';
import { fetchTicker } from '../api/marketApi';
import type { TickerData } from '../types';
import { usePolling, type PollingState } from './usePolling';

const TICKER_POLL_MS = 5_000;

export function useTicker(symbol: string): PollingState<TickerData> {
  const fetcher = useCallback(() => fetchTicker(symbol), [symbol]);
  return usePolling(fetcher, TICKER_POLL_MS);
}
