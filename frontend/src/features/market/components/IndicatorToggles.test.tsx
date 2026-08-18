import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_VISIBILITY, IndicatorToggles } from './IndicatorToggles';

afterEach(cleanup);

describe('DEFAULT_VISIBILITY', () => {
  it('starts with every indicator visible', () => {
    expect(DEFAULT_VISIBILITY).toEqual({
      sma200: true,
      ema15: true,
      ema30: true,
      ema45: true,
      ema60: true,
      rsi14: true,
      macd: true,
    });
  });
});

describe('IndicatorToggles', () => {
  it('renders a labelled checkbox per indicator', () => {
    render(<IndicatorToggles value={DEFAULT_VISIBILITY} onChange={() => {}} />);

    const labels = ['SMA 200', 'EMA 15', 'EMA 30', 'EMA 45', 'EMA 60', 'RSI 14', 'MACD'];
    for (const label of labels) {
      const checkbox = screen.getByLabelText(label) as HTMLInputElement;
      expect(checkbox.type).toBe('checkbox');
      expect(checkbox.checked).toBe(true);
    }
  });

  it('reflects the visibility it is given', () => {
    render(
      <IndicatorToggles
        value={{ ...DEFAULT_VISIBILITY, rsi14: false }}
        onChange={() => {}}
      />,
    );

    expect((screen.getByLabelText('RSI 14') as HTMLInputElement).checked).toBe(false);
    expect((screen.getByLabelText('MACD') as HTMLInputElement).checked).toBe(true);
  });

  it('flips only the toggled indicator', () => {
    const onChange = vi.fn();
    render(<IndicatorToggles value={DEFAULT_VISIBILITY} onChange={onChange} />);

    screen.getByLabelText('EMA 30').click();

    expect(onChange).toHaveBeenCalledWith({ ...DEFAULT_VISIBILITY, ema30: false });
  });
});
