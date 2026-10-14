import { describe, expect, it } from 'vitest';
import {
  highestLevels,
  levelChoices,
  levelsForRequest,
  ownLevels,
  researchBlock,
  sharedLevels,
  statsAt,
  toLevelMap,
} from './unitLevels';

describe('toLevelMap / ownLevels', () => {
  it('keeps unit types above level 1 only', () => {
    expect(toLevelMap({ SPEARMAN: 2, AXEMAN: 1, NOT_A_UNIT: 3 })).toEqual({ SPEARMAN: 2 });
    expect(toLevelMap(null)).toEqual({});
    expect(
      ownLevels([
        { unit: 'SPEARMAN', level: 3 },
        { unit: 'SCOUT', level: 1 },
      ]),
    ).toEqual({ SPEARMAN: 3 });
  });
});

describe('levelsForRequest', () => {
  it('sends levels above 1, and null when there are none', () => {
    expect(levelsForRequest({ AXEMAN: 2, SPEARMAN: 1 })).toEqual({ AXEMAN: 2 });
    expect(levelsForRequest({})).toBeNull();
  });
});

describe('sharedLevels', () => {
  const stack = (units: Record<string, number>, levels: Record<string, number>) => ({ units, levels });

  it('shows a level when every stack with the unit has it', () => {
    expect(
      sharedLevels([stack({ SPEARMAN: 5 }, { SPEARMAN: 2 }), stack({ SPEARMAN: 9, AXEMAN: 1 }, { SPEARMAN: 2 })]),
    ).toEqual({ SPEARMAN: 2 });
  });

  it('shows none where the stacks differ, and counts a missing entry as level 1', () => {
    expect(sharedLevels([stack({ SPEARMAN: 5 }, { SPEARMAN: 2 }), stack({ SPEARMAN: 9 }, {})])).toEqual({});
  });

  it('ignores stacks without the unit', () => {
    expect(sharedLevels([stack({ AXEMAN: 5 }, {}), stack({ SPEARMAN: 9 }, { SPEARMAN: 3 })])).toEqual({ SPEARMAN: 3 });
  });
});

describe('highestLevels', () => {
  it('takes the highest level per unit', () => {
    expect(highestLevels([{ SPEARMAN: 2 }, { SPEARMAN: 3, AXEMAN: 2 }, {}])).toEqual({ SPEARMAN: 3, AXEMAN: 2 });
  });
});

describe('researchBlock', () => {
  const ready = { missing: [], villageBusy: false, affordable: true };

  it('is null when nothing is in the way', () => {
    expect(researchBlock(ready)).toBeNull();
  });

  it('names the reasons in order: requirements, busy smithy, resources', () => {
    expect(researchBlock({ missing: [{}], villageBusy: true, affordable: false })).toBe('requirements');
    expect(researchBlock({ ...ready, villageBusy: true, affordable: false })).toBe('busy');
    expect(researchBlock({ ...ready, affordable: false })).toBe('resources');
  });
});

describe('statsAt / levelChoices', () => {
  it('reads level n from entry n - 1 and clamps', () => {
    const stats = ['a', 'b', 'c'];
    expect(statsAt(stats, 1)).toBe('a');
    expect(statsAt(stats, 3)).toBe('c');
    expect(statsAt(stats, 9)).toBe('c');
    expect(statsAt(stats, 0)).toBe('a');
  });

  it('lists 1 to the top level', () => {
    expect(levelChoices(3)).toEqual([1, 2, 3]);
    expect(levelChoices(1)).toEqual([1]);
  });
});
