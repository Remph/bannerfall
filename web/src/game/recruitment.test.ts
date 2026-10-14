import { describe, expect, it } from 'vitest';
import {
  MAX_RECRUIT_COUNT,
  maxAffordable,
  nextUnitAtMs,
  nextUnitDueMs,
  sharedMax,
  timesCount,
  totalOf,
  unitsDone,
  type RecruitOrderTiming,
} from './recruitment';

const now = Date.parse('2026-10-04T12:00:00Z');
const second = 1000;

/** An order of `count` units, started `startedSecondsAgo` seconds ago (negative = starts later). */
function order(
  count: number,
  secondsPerUnit: number,
  startedSecondsAgo: number,
  completedCount = 0,
): RecruitOrderTiming {
  return { count, completedCount, secondsPerUnit, startAt: new Date(now - startedSecondsAgo * second).toISOString() };
}

describe('unitsDone', () => {
  it('counts whole units since the start', () => {
    expect(unitsDone(order(10, 60, 0), now)).toBe(0);
    expect(unitsDone(order(10, 60, 59), now)).toBe(0);
    expect(unitsDone(order(10, 60, 60), now)).toBe(1);
    expect(unitsDone(order(10, 60, 179), now)).toBe(2);
  });

  it('uses whole elapsed seconds, like the server', () => {
    // 59.999 s are 59 whole seconds: the first unit (60 s) isn't done yet
    expect(unitsDone(order(10, 60, 0), now + 59_999)).toBe(0);
    expect(unitsDone(order(10, 60, 0), now + 60_000)).toBe(1);
  });

  it('is 0 before the start and at most the count', () => {
    expect(unitsDone(order(10, 60, -300), now)).toBe(0);
    expect(unitsDone(order(3, 60, 3600), now)).toBe(3);
  });
});

describe('nextUnitAtMs', () => {
  it('is the end of the next unit', () => {
    expect(nextUnitAtMs(order(10, 60, 0), 0)).toBe(now + 60 * second);
    expect(nextUnitAtMs(order(10, 60, 90), 1)).toBe(now + 30 * second);
  });

  it('is undefined when every unit is done', () => {
    expect(nextUnitAtMs(order(3, 60, 600), 3)).toBeUndefined();
  });
});

describe('nextUnitDueMs', () => {
  it('takes the earliest unit the server has not counted yet', () => {
    const barracks = order(10, 100, 250, 2); // unit 3 at start + 300 s = now + 50 s
    const stable = order(5, 400, 0); // unit 1 at now + 400 s
    expect(nextUnitDueMs([stable, barracks])).toBe(now + 50 * second);
  });

  it('can be in the past when the answer is older than the next unit', () => {
    expect(nextUnitDueMs([order(10, 60, 150, 1)])).toBe(now - 30 * second);
  });

  it('is undefined without orders', () => {
    expect(nextUnitDueMs([])).toBeUndefined();
  });
});

describe('maxAffordable', () => {
  const spearman = { wood: 50, clay: 30, iron: 10 };

  it('is limited by the scarcest resource', () => {
    expect(maxAffordable({ wood: 520, clay: 900, iron: 900 }, spearman, 100, 1)).toBe(10);
    expect(maxAffordable({ wood: 900, clay: 95, iron: 900 }, spearman, 100, 1)).toBe(3);
  });

  it('rounds the stock down, like the server compares it', () => {
    expect(maxAffordable({ wood: 99.9, clay: 900, iron: 900 }, spearman, 100, 1)).toBe(1);
  });

  it('is limited by the free population', () => {
    expect(maxAffordable({ wood: 9000, clay: 9000, iron: 9000 }, spearman, 7, 2)).toBe(3);
  });

  it('is never negative (more population used than the farm has)', () => {
    expect(maxAffordable({ wood: 9000, clay: 9000, iron: 9000 }, spearman, -5, 1)).toBe(0);
    expect(maxAffordable({ wood: 9000, clay: 9000, iron: 9000 }, spearman, -1, 2)).toBe(0);
  });

  it('is at most the API limit', () => {
    const plenty = { wood: 1e9, clay: 1e9, iron: 1e9 };
    expect(maxAffordable(plenty, spearman, 1e9, 1)).toBe(MAX_RECRUIT_COUNT);
  });
});

describe('timesCount', () => {
  it('multiplies every resource', () => {
    expect(timesCount({ wood: 50, clay: 30, iron: 10 }, 12)).toEqual({ wood: 600, clay: 360, iron: 120 });
  });
});

describe('shared limits (M27)', () => {
  const spearman = { cost: { wood: 50, clay: 30, iron: 10 }, pop: 1 };
  const swordsman = { cost: { wood: 30, clay: 30, iron: 70 }, pop: 1 };
  const scout = { cost: { wood: 50, clay: 50, iron: 20 }, pop: 2 };
  const stock = { wood: 500.7, clay: 500, iron: 500 };

  it('adds up cost and population of the typed rows', () => {
    expect(totalOf([])).toEqual({ cost: { wood: 0, clay: 0, iron: 0 }, pop: 0 });
    expect(
      totalOf([
        { ...spearman, count: 4 },
        { ...scout, count: 2 },
      ]),
    ).toEqual({
      cost: { wood: 300, clay: 220, iron: 80 },
      pop: 8,
    });
  });

  it('is the plain maximum without other rows', () => {
    expect(sharedMax(stock, 200, spearman, [])).toBe(maxAffordable(stock, spearman.cost, 200, 1));
    expect(sharedMax(stock, 200, spearman, [{ ...swordsman, count: 0 }])).toBe(10);
  });

  it('lowers the maximum by what the other rows cost', () => {
    // 4 spearmen take 200 wood, 120 clay, 40 iron; the swordsmen are bounded by iron: 460 / 70
    expect(sharedMax(stock, 200, swordsman, [{ ...spearman, count: 4 }])).toBe(6);
    // and back: 6 swordsmen take 180 wood, the spearmen get (500 - 180) / 50
    expect(sharedMax(stock, 200, spearman, [{ ...swordsman, count: 6 }])).toBe(6);
  });

  it('shares the free population', () => {
    const rich = { wood: 100_000, clay: 100_000, iron: 100_000 };
    expect(sharedMax(rich, 5, scout, [{ ...spearman, count: 3 }])).toBe(1);
    expect(sharedMax(rich, 5, scout, [{ ...spearman, count: 5 }])).toBe(0);
  });

  it('is never negative when the others already take too much', () => {
    expect(sharedMax(stock, 200, spearman, [{ ...swordsman, count: 50 }])).toBe(0);
  });
});
