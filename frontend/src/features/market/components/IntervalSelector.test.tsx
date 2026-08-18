import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { INTERVALS } from '../types';
import { IntervalSelector } from './IntervalSelector';

afterEach(cleanup);

describe('IntervalSelector', () => {
  it('renders one button per supported interval', () => {
    render(<IntervalSelector value="1h" onChange={() => {}} />);

    for (const interval of INTERVALS) {
      expect(screen.getByRole('button', { name: interval })).toBeTruthy();
    }
  });

  it('marks only the selected interval as pressed', () => {
    render(<IntervalSelector value="4h" onChange={() => {}} />);

    expect(screen.getByRole('button', { name: '4h' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: '1h' }).getAttribute('aria-pressed')).toBe('false');
  });

  it('reports the clicked interval', () => {
    const onChange = vi.fn();
    render(<IntervalSelector value="1h" onChange={onChange} />);

    screen.getByRole('button', { name: '15m' }).click();

    expect(onChange).toHaveBeenCalledWith('15m');
  });
});
