import type { TickerData } from '../types';

interface Props {
  ticker: TickerData | null;
  isStale: boolean;
  errorMessage: string | null;
}

function format(value: number, digits = 2): string {
  return value.toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

export function PriceHeader({ ticker, isStale, errorMessage }: Props) {
  if (!ticker) {
    return (
      <div className="price-header">
        <span className="price-header__symbol">BTC/USDT</span>
        <span className="price-header__stat">載入中…</span>
        {errorMessage && <span className="badge badge--error">{errorMessage}</span>}
      </div>
    );
  }

  const up = ticker.changePercent24h >= 0;

  return (
    <div className="price-header">
      <span className="price-header__symbol">BTC/USDT</span>
      <span className="price-header__price">${format(ticker.price)}</span>
      <span className={up ? 'price-header__change--up' : 'price-header__change--down'}>
        {up ? '+' : ''}
        {format(ticker.changePercent24h)}%
      </span>
      <span className="price-header__stat">
        24h 高 <span>{format(ticker.high24h)}</span>
      </span>
      <span className="price-header__stat">
        24h 低 <span>{format(ticker.low24h)}</span>
      </span>
      <span className="price-header__stat">
        24h 量 <span>{format(ticker.volume24h)}</span>
      </span>
      {isStale && <span className="badge badge--stale">資料延遲</span>}
      {errorMessage && <span className="badge badge--error">{errorMessage}</span>}
    </div>
  );
}
