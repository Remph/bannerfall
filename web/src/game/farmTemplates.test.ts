import { describe, expect, it } from 'vitest';
import { nextFreeLetter, unitsOfChoice, type FarmTemplate } from './farmTemplates';

const a: FarmTemplate = { letter: 'A', units: { LIGHT_CAVALRY: 5 } };
const c: FarmTemplate = { letter: 'C', units: { AXEMAN: 20 } };

describe('nextFreeLetter', () => {
  it('offers the first letter not taken, also in a gap', () => {
    expect(nextFreeLetter([])).toBe('A');
    expect(nextFreeLetter([a, c])).toBe('B');
  });

  it('offers none when all five are there', () => {
    const all = (['A', 'B', 'C', 'D', 'E'] as const).map((letter) => ({ letter, units: { SCOUT: 1 } }));
    expect(nextFreeLetter(all)).toBeUndefined();
  });
});

describe('unitsOfChoice', () => {
  it('takes the template’s units, or the own counts for null', () => {
    expect(unitsOfChoice('C', [a, c], { SPEARMAN: 3 })).toEqual({ AXEMAN: 20 });
    expect(unitsOfChoice(null, [a, c], { SPEARMAN: 3 })).toEqual({ SPEARMAN: 3 });
  });

  it('gives nothing for a template that is gone', () => {
    expect(unitsOfChoice('B', [a, c], { SPEARMAN: 3 })).toEqual({});
  });
});
