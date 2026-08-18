/** Mirrors the backend DTOs in com.happytrade.market.web. */

export const INTERVALS = ['1m', '5m', '15m', '1h', '4h', '1d'] as const;

export type IntervalCode = (typeof INTERVALS)[number];

export interface Candle {
  /** Unix seconds — lightweight-charts' native time format. */
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface TickerData {
  symbol: string;
  price: number;
  changePercent24h: number;
  high24h: number;
  low24h: number;
  volume24h: number;
  /** Server observation time, ISO-8601. */
  timestamp: string;
}

export interface MacdSeries {
  macd: (number | null)[];
  signal: (number | null)[];
  histogram: (number | null)[];
}

export interface Indicators {
  sma200: (number | null)[];
  ema15: (number | null)[];
  ema30: (number | null)[];
  ema45: (number | null)[];
  ema60: (number | null)[];
  rsi14: (number | null)[];
  macd: MacdSeries;
}

export interface ChartData {
  symbol: string;
  interval: IntervalCode;
  candles: Candle[];
  /**
   * Every array here has exactly the same length as `candles`, and index i corresponds to
   * candles[i]. Apply no offset.
   */
  indicators: Indicators;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  retryAfter?: number;
}
