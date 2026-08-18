import {
  CandlestickSeries,
  createChart,
  HistogramSeries,
  LineSeries,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import { useEffect, useRef } from 'react';
import type { ChartData } from '../types';
import type { IndicatorVisibility } from './IndicatorToggles';

const PANE_PRICE = 0;
const PANE_VOLUME = 1;
const PANE_RSI = 2;
const PANE_MACD = 3;

const EMA_COLORS = {
  ema15: '#f0b429',
  ema30: '#4dabf7',
  ema45: '#b197fc',
  ema60: '#63e6be',
} as const;

/** Drops null gaps — lightweight-charts renders a break wherever a point is absent. */
export function toLine(candles: ChartData['candles'], values: (number | null)[]) {
  const points: { time: UTCTimestamp; value: number }[] = [];
  for (let i = 0; i < candles.length; i++) {
    const value = values[i];
    if (value !== null && value !== undefined) {
      points.push({ time: candles[i].time as UTCTimestamp, value });
    }
  }
  return points;
}

interface Props {
  data: ChartData | null;
  visibility: IndicatorVisibility;
}

/**
 * Series handles are held in one named, precisely-typed object rather than a loose record.
 * A `Record<string, ISeriesApi<'Candlestick' | 'Histogram' | 'Line'>>` forces a cast at every
 * `setData` call, and those casts are exactly what would hide a mistake like feeding line points
 * to the candlestick series.
 */
interface ChartSeries {
  price: ISeriesApi<'Candlestick'>;
  volume: ISeriesApi<'Histogram'>;
  sma200: ISeriesApi<'Line'>;
  ema15: ISeriesApi<'Line'>;
  ema30: ISeriesApi<'Line'>;
  ema45: ISeriesApi<'Line'>;
  ema60: ISeriesApi<'Line'>;
  rsi14: ISeriesApi<'Line'>;
  macd: ISeriesApi<'Line'>;
  macdSignal: ISeriesApi<'Line'>;
  macdHistogram: ISeriesApi<'Histogram'>;
}

export function PriceChart({ data, visibility }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ChartSeries | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { color: '#161b22' },
        textColor: '#8b949e',
        panes: { separatorColor: '#30363d', separatorHoverColor: '#484f58' },
      },
      grid: {
        vertLines: { color: '#21262d' },
        horzLines: { color: '#21262d' },
      },
      timeScale: { timeVisible: true, secondsVisible: false },
    });
    chartRef.current = chart;

    const price = chart.addSeries(
      CandlestickSeries,
      {
        upColor: '#26a69a',
        downColor: '#ef5350',
        borderVisible: false,
        wickUpColor: '#26a69a',
        wickDownColor: '#ef5350',
      },
      PANE_PRICE,
    );
    const volume = chart.addSeries(
      HistogramSeries,
      { color: '#4a5568', priceFormat: { type: 'volume' } },
      PANE_VOLUME,
    );
    const sma200 = chart.addSeries(LineSeries, { color: '#ffffff', lineWidth: 2 }, PANE_PRICE);
    const ema15 = chart.addSeries(LineSeries, { color: EMA_COLORS.ema15, lineWidth: 1 }, PANE_PRICE);
    const ema30 = chart.addSeries(LineSeries, { color: EMA_COLORS.ema30, lineWidth: 1 }, PANE_PRICE);
    const ema45 = chart.addSeries(LineSeries, { color: EMA_COLORS.ema45, lineWidth: 1 }, PANE_PRICE);
    const ema60 = chart.addSeries(LineSeries, { color: EMA_COLORS.ema60, lineWidth: 1 }, PANE_PRICE);
    const rsi14 = chart.addSeries(LineSeries, { color: '#d19a66', lineWidth: 1 }, PANE_RSI);
    const macd = chart.addSeries(LineSeries, { color: '#4dabf7', lineWidth: 1 }, PANE_MACD);
    const macdSignal = chart.addSeries(LineSeries, { color: '#f0b429', lineWidth: 1 }, PANE_MACD);
    const macdHistogram = chart.addSeries(HistogramSeries, { color: '#4a5568' }, PANE_MACD);

    rsi14.createPriceLine({ price: 70, color: '#ef5350', lineWidth: 1, title: '70' });
    rsi14.createPriceLine({ price: 30, color: '#26a69a', lineWidth: 1, title: '30' });

    seriesRef.current = {
      price,
      volume,
      sma200,
      ema15,
      ema30,
      ema45,
      ema60,
      rsi14,
      macd,
      macdSignal,
      macdHistogram,
    };

    // Pane heights follow the 60 / 15 / 12 / 13 split from the spec. Stretch factors rather than
    // pixels: this effect runs before flex layout settles, so `container.clientHeight` is still the
    // CSS min-height and a pixel split computed from it leaves the price pane oversized. Factors
    // are proportional, so they also survive every later resize.
    const panes = chart.panes();
    panes[PANE_PRICE]?.setStretchFactor(60);
    panes[PANE_VOLUME]?.setStretchFactor(15);
    panes[PANE_RSI]?.setStretchFactor(12);
    panes[PANE_MACD]?.setStretchFactor(13);

    return () => {
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, []);

  useEffect(() => {
    const s = seriesRef.current;
    if (!data || !s) return;
    const { candles, indicators } = data;

    s.price.setData(
      candles.map((c) => ({
        time: c.time as UTCTimestamp,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      })),
    );

    s.volume.setData(
      candles.map((c) => ({
        time: c.time as UTCTimestamp,
        value: c.volume,
        color: c.close >= c.open ? 'rgba(38,166,154,0.5)' : 'rgba(239,83,80,0.5)',
      })),
    );

    s.sma200.setData(toLine(candles, indicators.sma200));
    s.ema15.setData(toLine(candles, indicators.ema15));
    s.ema30.setData(toLine(candles, indicators.ema30));
    s.ema45.setData(toLine(candles, indicators.ema45));
    s.ema60.setData(toLine(candles, indicators.ema60));
    s.rsi14.setData(toLine(candles, indicators.rsi14));
    s.macd.setData(toLine(candles, indicators.macd.macd));
    s.macdSignal.setData(toLine(candles, indicators.macd.signal));

    s.macdHistogram.setData(
      toLine(candles, indicators.macd.histogram).map((point) => ({
        ...point,
        color: point.value >= 0 ? 'rgba(38,166,154,0.6)' : 'rgba(239,83,80,0.6)',
      })),
    );
  }, [data]);

  useEffect(() => {
    const s = seriesRef.current;
    if (!s) return;

    s.sma200.applyOptions({ visible: visibility.sma200 });
    s.ema15.applyOptions({ visible: visibility.ema15 });
    s.ema30.applyOptions({ visible: visibility.ema30 });
    s.ema45.applyOptions({ visible: visibility.ema45 });
    s.ema60.applyOptions({ visible: visibility.ema60 });
    s.rsi14.applyOptions({ visible: visibility.rsi14 });
    s.macd.applyOptions({ visible: visibility.macd });
    s.macdSignal.applyOptions({ visible: visibility.macd });
    s.macdHistogram.applyOptions({ visible: visibility.macd });
  }, [visibility]);

  return <div className="chart-container" ref={containerRef} />;
}
