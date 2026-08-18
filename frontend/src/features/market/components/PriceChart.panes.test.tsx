import { cleanup, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Pane proportions must come from stretch factors, not from pixel heights measured at mount.
 * `container.clientHeight` is still the CSS `min-height` when the effect runs, so a pixel split
 * computed from it leaves the price pane oversized once flex layout settles.
 */
const panes = [0, 1, 2, 3].map(() => ({
  setStretchFactor: vi.fn(),
  setHeight: vi.fn(),
}));

vi.mock('lightweight-charts', () => {
  const makeSeries = () => ({
    setData: vi.fn(),
    applyOptions: vi.fn(),
    createPriceLine: vi.fn(),
  });
  return {
    createChart: vi.fn(() => ({
      addSeries: vi.fn(() => makeSeries()),
      panes: vi.fn(() => panes),
      remove: vi.fn(),
    })),
    CandlestickSeries: 'CandlestickSeries',
    HistogramSeries: 'HistogramSeries',
    LineSeries: 'LineSeries',
  };
});

const { DEFAULT_VISIBILITY } = await import('./IndicatorToggles');
const { PriceChart } = await import('./PriceChart');

beforeEach(() => {
  for (const pane of panes) {
    pane.setStretchFactor.mockClear();
    pane.setHeight.mockClear();
  }
});

afterEach(cleanup);

describe('PriceChart pane sizing', () => {
  it('splits the panes 60 / 15 / 12 / 13 by stretch factor', () => {
    render(<PriceChart data={null} visibility={DEFAULT_VISIBILITY} />);

    expect(panes[0].setStretchFactor).toHaveBeenCalledWith(60);
    expect(panes[1].setStretchFactor).toHaveBeenCalledWith(15);
    expect(panes[2].setStretchFactor).toHaveBeenCalledWith(12);
    expect(panes[3].setStretchFactor).toHaveBeenCalledWith(13);
  });

  it('never sizes a pane in pixels, which would be wrong before layout settles', () => {
    render(<PriceChart data={null} visibility={DEFAULT_VISIBILITY} />);

    for (const pane of panes) {
      expect(pane.setHeight).not.toHaveBeenCalled();
    }
  });
});
