import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { TickerData } from '../types';
import { PriceHeader } from './PriceHeader';

afterEach(cleanup);

const ticker: TickerData = {
  symbol: 'BTCUSDT',
  price: 65432.1,
  changePercent24h: 1.25,
  high24h: 66000,
  low24h: 64000,
  volume24h: 12345.678,
  timestamp: '2026-08-18T07:00:00Z',
};

describe('PriceHeader', () => {
  it('shows a loading hint while no ticker has arrived', () => {
    render(<PriceHeader ticker={null} isStale={false} errorMessage={null} />);

    expect(screen.getByText('載入中…')).toBeTruthy();
    expect(screen.queryByText(/\$/)).toBeNull();
  });

  it('formats the price and marks a positive change as up', () => {
    render(<PriceHeader ticker={ticker} isStale={false} errorMessage={null} />);

    expect(screen.getByText('$65,432.10')).toBeTruthy();
    const change = screen.getByText('+1.25%');
    expect(change.className).toBe('price-header__change--up');
  });

  it('marks a negative change as down', () => {
    render(
      <PriceHeader
        ticker={{ ...ticker, changePercent24h: -2.5 }}
        isStale={false}
        errorMessage={null}
      />,
    );

    const change = screen.getByText('-2.50%');
    expect(change.className).toBe('price-header__change--down');
  });

  it('shows the stale badge only when the data is stale', () => {
    const { rerender } = render(
      <PriceHeader ticker={ticker} isStale={false} errorMessage={null} />,
    );
    expect(screen.queryByText('資料延遲')).toBeNull();

    rerender(<PriceHeader ticker={ticker} isStale={true} errorMessage={null} />);
    expect(screen.getByText('資料延遲')).toBeTruthy();
  });

  it('shows the error badge with or without a ticker', () => {
    const { rerender } = render(
      <PriceHeader ticker={null} isStale={false} errorMessage="此網路無法連線交易所" />,
    );
    expect(screen.getByText('此網路無法連線交易所')).toBeTruthy();

    rerender(<PriceHeader ticker={ticker} isStale={false} errorMessage="此網路無法連線交易所" />);
    expect(screen.getByText('此網路無法連線交易所')).toBeTruthy();
  });
});
