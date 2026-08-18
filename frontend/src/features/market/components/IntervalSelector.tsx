import { INTERVALS, type IntervalCode } from '../types';

interface Props {
  value: IntervalCode;
  onChange: (interval: IntervalCode) => void;
}

export function IntervalSelector({ value, onChange }: Props) {
  return (
    <div className="interval-selector">
      {INTERVALS.map((interval) => (
        <button
          key={interval}
          type="button"
          aria-pressed={interval === value}
          onClick={() => onChange(interval)}
        >
          {interval}
        </button>
      ))}
    </div>
  );
}
