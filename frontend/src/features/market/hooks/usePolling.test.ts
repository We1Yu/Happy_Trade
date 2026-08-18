import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MarketApiError } from '../api/marketApi';
import { usePolling } from './usePolling';

/** Lets a test flip the tab between visible and hidden. */
function setVisibility(state: DocumentVisibilityState) {
  Object.defineProperty(document, 'visibilityState', { value: state, configurable: true });
  document.dispatchEvent(new Event('visibilitychange'));
}

/** Advances timers and lets the pending fetch promises settle inside act(). */
async function advance(ms: number) {
  await act(async () => {
    vi.advanceTimersByTime(ms);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  setVisibility('visible');
});

afterEach(() => {
  vi.useRealTimers();
});

describe('usePolling', () => {
  it('fetches once immediately and exposes the payload', async () => {
    const fetcher = vi.fn().mockResolvedValue('first');

    const { result } = renderHook(() => usePolling(fetcher, 5_000));
    await advance(0);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(result.current.data).toBe('first');
    expect(result.current.error).toBeNull();
    expect(result.current.isStale).toBe(false);
  });

  it('re-fetches every intervalMs', async () => {
    const fetcher = vi.fn().mockResolvedValue('tick');

    renderHook(() => usePolling(fetcher, 5_000));
    await advance(0);
    await advance(10_000);

    expect(fetcher).toHaveBeenCalledTimes(3);
  });

  it('keeps the last good payload and marks it stale when a poll fails', async () => {
    const failure = new MarketApiError({ code: 'UPSTREAM_TIMEOUT', message: 'too slow' });
    const fetcher = vi.fn().mockResolvedValueOnce('good').mockRejectedValue(failure);

    const { result } = renderHook(() => usePolling(fetcher, 5_000));
    await advance(0);
    await advance(5_000);

    expect(result.current.data).toBe('good');
    expect(result.current.error).toBe(failure);
    expect(result.current.isStale).toBe(true);
  });

  it('clears the error once a later poll succeeds', async () => {
    const fetcher = vi
      .fn()
      .mockRejectedValueOnce(new MarketApiError({ code: 'UPSTREAM_TIMEOUT', message: 'too slow' }))
      .mockResolvedValue('recovered');

    const { result } = renderHook(() => usePolling(fetcher, 5_000));
    await advance(0);
    await advance(5_000);

    expect(result.current.data).toBe('recovered');
    expect(result.current.error).toBeNull();
    expect(result.current.isStale).toBe(false);
  });

  it('wraps a non-MarketApiError rejection so callers always see MarketApiError', async () => {
    const fetcher = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

    const { result } = renderHook(() => usePolling(fetcher, 5_000));
    await advance(0);

    expect(result.current.error).toBeInstanceOf(MarketApiError);
    expect(result.current.error?.code).toBe('NETWORK');
  });

  it('stops polling while the tab is hidden and fetches once on return', async () => {
    const fetcher = vi.fn().mockResolvedValue('tick');

    renderHook(() => usePolling(fetcher, 5_000));
    await advance(0);
    expect(fetcher).toHaveBeenCalledTimes(1);

    act(() => setVisibility('hidden'));
    await advance(20_000);
    expect(fetcher).toHaveBeenCalledTimes(1);

    await act(async () => setVisibility('visible'));
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('stops polling after unmount', async () => {
    const fetcher = vi.fn().mockResolvedValue('tick');

    const { unmount } = renderHook(() => usePolling(fetcher, 5_000));
    await advance(0);
    unmount();
    await advance(20_000);

    expect(fetcher).toHaveBeenCalledTimes(1);
  });
});
