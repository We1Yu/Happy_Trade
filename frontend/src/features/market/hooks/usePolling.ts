import { useEffect, useRef, useState } from 'react';
import { MarketApiError } from '../api/marketApi';

export interface PollingState<T> {
  data: T | null;
  error: MarketApiError | null;
  /** True when the last attempt failed but earlier data is still on screen. */
  isStale: boolean;
}

export function usePolling<T>(fetcher: () => Promise<T>, intervalMs: number): PollingState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<MarketApiError | null>(null);

  // Kept in a ref so changing the fetcher identity does not restart the timer.
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;

    const run = async () => {
      try {
        const result = await fetcherRef.current();
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
  }, [intervalMs]);

  return { data, error, isStale: error !== null && data !== null };
}
