import { useEffect, useState } from 'react';
import { MarketApiError } from '../api/marketApi';

export interface PollingState<T> {
  data: T | null;
  error: MarketApiError | null;
  /** True when the last attempt failed but earlier data is still on screen. */
  isStale: boolean;
}

/**
 * `fetcher` must be memoised by the caller — its identity is a dependency, so a fresh closure on
 * every render would restart the poll on every render. `useTicker` and `useChartData` wrap it in
 * `useCallback`, which makes the identity change exactly when the request itself changes. Keying
 * the effect on the cadence alone is not enough: 15m and 1h both poll every 60s, so switching
 * between them would leave the previous interval's chart on screen until the next scheduled tick.
 */
export function usePolling<T>(fetcher: () => Promise<T>, intervalMs: number): PollingState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<MarketApiError | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;

    const run = async () => {
      try {
        const result = await fetcher();
        if (cancelled) return;
        setData(result);
        setError(null);
      } catch (e) {
        if (cancelled) return;
        // Deliberately does not clear `data` — the last good payload stays on screen.
        setError(
          e instanceof MarketApiError
            ? e
            : new MarketApiError({ code: 'NETWORK', message: String(e) }),
        );
      }
    };

    const start = () => {
      void run();
      timer = window.setInterval(run, intervalMs);
    };

    const stop = () => {
      if (timer !== undefined) {
        window.clearInterval(timer);
        timer = undefined;
      }
    };

    const onVisibilityChange = () => {
      stop();
      if (document.visibilityState === 'visible') {
        start();
      }
    };

    if (document.visibilityState === 'visible') {
      start();
    }
    document.addEventListener('visibilitychange', onVisibilityChange);

    return () => {
      cancelled = true;
      stop();
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [fetcher, intervalMs]);

  return { data, error, isStale: error !== null && data !== null };
}
