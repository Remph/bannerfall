import { describe, expect, it } from 'vitest';
import { formatMorale } from './morale';

describe('formatMorale', () => {
  it('shows the whole percent the server rounds to', () => {
    expect(formatMorale(0.48, 'en')).toBe('48%');
    expect(formatMorale(0.3, 'de')).toBe('30\u00a0%');
    // 0.29 × 100 is 28.999…: still 29
    expect(formatMorale(0.29, 'en')).toBe('29%');
  });
});
