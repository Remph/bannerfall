import { describe, expect, it } from 'vitest';
import {
  ordersOf,
  planSeconds,
  planTemplate,
  type PlanBuilding,
  type PlanInput,
  type PlanUnit,
} from './recruitTemplates';

const unit = (name: PlanUnit['unit'], wood: number, pop: number, secondsPerUnit: number): PlanUnit => ({
  unit: name,
  cost: { wood, clay: wood, iron: wood },
  pop,
  secondsPerUnit,
  available: true,
});

const axe = unit('AXEMAN', 50, 1, 100);
const spear = unit('SPEARMAN', 40, 1, 80);
const light = unit('LIGHT_CAVALRY', 125, 4, 200);
const paladin = unit('PALADIN', 1000, 10, 1000);

const barracks: PlanBuilding = { building: 'BARRACKS', level: 10, units: [spear, axe], queue: [] };
const stable: PlanBuilding = { building: 'STABLE', level: 5, units: [light], queue: [] };

const rich = { wood: 1_000_000, clay: 1_000_000, iron: 1_000_000 };

function input(overrides: Partial<PlanInput>): PlanInput {
  return {
    goal: {},
    buildings: [barracks, stable],
    home: {},
    away: {},
    stock: rich,
    freePop: 100_000,
    queueSize: 5,
    freeSlots: undefined,
    ...overrides,
  };
}

describe('planTemplate', () => {
  it('recruits what the goal lacks: units at home, away and in training count', () => {
    const lines = planTemplate(
      input({
        goal: { AXEMAN: 100, LIGHT_CAVALRY: 40 },
        buildings: [{ ...barracks, queue: [{ unit: 'AXEMAN', count: 30, completedCount: 10 }] }, stable],
        home: { AXEMAN: 50 },
        away: { AXEMAN: 5, LIGHT_CAVALRY: 40 },
      }),
    );
    expect(lines.map((line) => [line.unit.unit, line.have, line.missing, line.now, line.skip])).toEqual([
      ['AXEMAN', 75, 25, 25, null],
      ['LIGHT_CAVALRY', 40, 0, 0, 'reached'],
    ]);
  });

  it('cuts every unit by the same share when the stock is short', () => {
    // the goal costs 100 × 50 + 40 × 125 = 10 000 of each resource, the stock pays half
    const lines = planTemplate(
      input({ goal: { AXEMAN: 100, LIGHT_CAVALRY: 40 }, stock: { wood: 5000.9, clay: 9000, iron: 9000 } }),
    );
    expect(lines.map((line) => line.now)).toEqual([50, 20]);
  });

  it('cuts by the free population too', () => {
    // 100 axes + 40 light cavalry need 260 population, 130 are free
    const lines = planTemplate(input({ goal: { AXEMAN: 100, LIGHT_CAVALRY: 40 }, freePop: 130 }));
    expect(lines.map((line) => line.now)).toEqual([50, 20]);
  });

  it('recruits nothing without resources or population', () => {
    expect(planTemplate(input({ goal: { AXEMAN: 10 }, freePop: -3 }))[0]?.now).toBe(0);
    expect(planTemplate(input({ goal: { AXEMAN: 10 }, stock: { wood: 0, clay: 0, iron: 0 } }))[0]?.now).toBe(0);
  });

  it('skips locked units and those of a building that is not built, and they cost nothing', () => {
    const lines = planTemplate(
      input({
        goal: { SPEARMAN: 10, AXEMAN: 10, LIGHT_CAVALRY: 10 },
        buildings: [
          { ...barracks, units: [spear, { ...axe, available: false }] },
          { ...stable, level: 0 },
        ],
        stock: { wood: 400, clay: 400, iron: 400 },
      }),
    );
    expect(lines.map((line) => [line.unit.unit, line.now, line.skip])).toEqual([
      ['SPEARMAN', 10, null],
      ['AXEMAN', 0, 'locked'],
      ['LIGHT_CAVALRY', 0, 'locked'],
    ]);
  });

  it('gives each recruited unit a free place in its queue, in unit order', () => {
    const full = { unit: 'SPEARMAN' as const, count: 1, completedCount: 0 };
    const lines = planTemplate(
      input({
        goal: { SPEARMAN: 10, AXEMAN: 10, LIGHT_CAVALRY: 10 },
        buildings: [{ ...barracks, queue: [full, full, full, full] }, stable],
      }),
    );
    expect(lines.map((line) => [line.unit.unit, line.skip])).toEqual([
      // the spearmen in the queue count as had: 4 of 10
      ['SPEARMAN', null],
      ['AXEMAN', 'queueFull'],
      ['LIGHT_CAVALRY', null],
    ]);
    expect(lines[0]?.now).toBe(6);
  });

  it('gives the paladin no more than the free slots', () => {
    const hall: PlanBuilding = { building: 'BANNER_HALL', level: 1, units: [paladin], queue: [] };
    const plan = (freeSlots: number) => planTemplate(input({ goal: { PALADIN: 3 }, buildings: [hall], freeSlots }))[0];
    expect(plan(1)).toMatchObject({ missing: 1, now: 1, skip: null });
    expect(plan(0)).toMatchObject({ missing: 0, now: 0, skip: 'slots' });
  });

  it('leaves out units the server does not list', () => {
    expect(planTemplate(input({ goal: { BANNER_THIEF: 5, AXEMAN: 1 } })).map((line) => line.unit.unit)).toEqual([
      'AXEMAN',
    ]);
  });
});

describe('ordersOf and planSeconds', () => {
  it('turns the recruiting lines into orders and takes the longest building', () => {
    const lines = planTemplate(input({ goal: { SPEARMAN: 10, AXEMAN: 10, LIGHT_CAVALRY: 3 }, home: { SPEARMAN: 10 } }));
    expect(ordersOf(lines)).toEqual([
      { unit: 'AXEMAN', count: 10 },
      { unit: 'LIGHT_CAVALRY', count: 3 },
    ]);
    // barracks 10 × 100 s, stable 3 × 200 s
    expect(planSeconds(lines)).toBe(1000);
    expect(planSeconds([])).toBe(0);
  });
});
