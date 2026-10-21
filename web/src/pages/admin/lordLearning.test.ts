import { describe, expect, it } from 'vitest';
import { mixChanges, percentOf, type LordLearning } from './lordLearning';

const learning: LordLearning = {
  defenceWeight: 0.296,
  attackWeight: 0.054,
  changes: [
    { unit: 'SPEARMAN', points: 14 },
    { unit: 'AXEMAN', points: -9 },
  ],
  walls: [],
};

describe('lordLearning', () => {
  it('rounds the lesson weights to whole percent', () => {
    expect(percentOf(learning.defenceWeight)).toBe(30);
    expect(percentOf(learning.attackWeight)).toBe(5);
  });

  it('lists the changes with their sign, a real minus and a space that keeps "%" on the line', () => {
    expect(mixChanges(learning, (unit) => unit.toLowerCase())).toBe('spearman +14\u00a0%, axeman −9\u00a0%');
  });

  it('is empty without changes', () => {
    expect(mixChanges({ ...learning, changes: [] }, (unit) => unit)).toBe('');
  });
});
