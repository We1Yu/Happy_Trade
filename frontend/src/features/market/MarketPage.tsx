import { useState } from 'react';
import {
  DEFAULT_VISIBILITY,
  IndicatorToggles,
  type IndicatorVisibility,
} from './components/IndicatorToggles';
import { IntervalSelector } from './components/IntervalSelector';
import { PriceChart } from './components/PriceChart';
import { PriceHeader } from './components/PriceHeader';
import { useChartData } from './hooks/useChartData';
import { useTicker } from './hooks/useTicker';
import type { IntervalCode } from './types';
import './market.css';

const SYMBOL = 'BTCUSDT';

export function MarketPage() {
  // Named `selectedInterval` rather than `interval` so `setInterval` does not shadow the global
  // timer function — a shadow that silently breaks any later code in this file that needs it.
  const [selectedInterval, setSelectedInterval] = useState<IntervalCode>('1h');
  const [visibility, setVisibility] = useState<IndicatorVisibility>(DEFAULT_VISIBILITY);

  const ticker = useTicker(SYMBOL);
  const chart = useChartData(SYMBOL, selectedInterval);

  // UPSTREAM_BLOCKED is terminal — retrying will not help, so it is surfaced as a hard error.
  const blocked =
    ticker.error?.code === 'UPSTREAM_BLOCKED' || chart.error?.code === 'UPSTREAM_BLOCKED';

  return (
    <div className="market-page">
      <PriceHeader
        ticker={ticker.data}
        isStale={ticker.isStale || chart.isStale}
        errorMessage={blocked ? '此網路無法連線交易所' : null}
      />
      <div className="control-row">
        <IntervalSelector value={selectedInterval} onChange={setSelectedInterval} />
        <IndicatorToggles value={visibility} onChange={setVisibility} />
      </div>
      <PriceChart data={chart.data} visibility={visibility} />
    </div>
  );
}
