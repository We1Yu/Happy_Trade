import type { ApiErrorBody, ChartData, IntervalCode, TickerData } from '../types';

export class MarketApiError extends Error {
  readonly code: string;
  readonly retryAfter?: number;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = 'MarketApiError';
    this.code = body.code;
    this.retryAfter = body.retryAfter;
  }
}

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url);

  if (!response.ok) {
    let body: ApiErrorBody;
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      body = { code: 'UNKNOWN', message: `Request failed with status ${response.status}` };
    }
    throw new MarketApiError(body);
  }

  return (await response.json()) as T;
}

export function fetchTicker(symbol = 'BTCUSDT'): Promise<TickerData> {
  return getJson<TickerData>(`/api/market/ticker?symbol=${symbol}`);
}

export function fetchChart(
  symbol: string,
  interval: IntervalCode,
  limit = 500,
): Promise<ChartData> {
  return getJson<ChartData>(
    `/api/market/chart?symbol=${symbol}&interval=${interval}&limit=${limit}`,
  );
}
