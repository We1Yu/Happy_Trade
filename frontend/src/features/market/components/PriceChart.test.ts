import { describe, expect, it } from 'vitest';
import type { Candle } from '../types';
import { toLine } from './PriceChart';

function candleAt(time: number): Candle {
  return { time, open: 1, high: 2, low: 0, close: 1.5, volume: 10 };
}

describe('toLine', () => {
  it('drops null gaps and keeps each value on its own candle time', () => {
    const candles = [candleAt(100), candleAt(200), candleAt(300), candleAt(400)];

    expect(toLine(candles, [null, 5, null, 7])).toEqual([
      { time: 200, value: 5 },
      { time: 400, value: 7 },
    ]);
  });

  it('returns nothing when the indicator is entirely undefined', () => {
    const candles = [candleAt(100), candleAt(200)];

    expect(toLine(candles, [null, null])).toEqual([]);
  });

  it('keeps a zero value, which is a real reading rather than a gap', () => {
    expect(toLine([candleAt(100)], [0])).toEqual([{ time: 100, value: 0 }]);
  });
});
