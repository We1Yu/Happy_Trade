export type IndicatorKey =
  | 'sma200'
  | 'ema15'
  | 'ema30'
  | 'ema45'
  | 'ema60'
  | 'rsi14'
  | 'macd';

export type IndicatorVisibility = Record<IndicatorKey, boolean>;

export const DEFAULT_VISIBILITY: IndicatorVisibility = {
  sma200: true,
  ema15: true,
  ema30: true,
  ema45: true,
  ema60: true,
  rsi14: true,
  macd: true,
};

const LABELS: Record<IndicatorKey, string> = {
  sma200: 'SMA 200',
  ema15: 'EMA 15',
  ema30: 'EMA 30',
  ema45: 'EMA 45',
  ema60: 'EMA 60',
  rsi14: 'RSI 14',
  macd: 'MACD',
};

interface Props {
  value: IndicatorVisibility;
  onChange: (value: IndicatorVisibility) => void;
}

export function IndicatorToggles({ value, onChange }: Props) {
  return (
    <div className="indicator-toggles">
      {(Object.keys(LABELS) as IndicatorKey[]).map((key) => (
        <label key={key}>
          <input
            type="checkbox"
            checked={value[key]}
            onChange={(e) => onChange({ ...value, [key]: e.target.checked })}
          />
          {LABELS[key]}
        </label>
      ))}
    </div>
  );
}
