import { describe, expect, it } from 'vitest';
import type { BuildingEffect, BuildingInfo } from '../api/queries/buildings';
import type { ResourceSnapshot } from './resources';
import { formatEffect, upgradeBlocker } from './upgradePreview';

const at = '2026-10-12T12:00:00Z';
const nowMs = Date.parse(at);
const hour = 3_600_000;

const village: ResourceSnapshot = {
  resources: { wood: 500, clay: 500, iron: 500, at },
  production: { wood: 30, clay: 30, iron: 30 },
  storageCapacity: 1000,
};

function info(
  overrides: Partial<BuildingInfo> = {},
  next: Partial<NonNullable<BuildingInfo['next']>> = {},
): BuildingInfo {
  return {
    building: 'WOODCUTTER',
    level: 1,
    queuedLevel: 1,
    maxLevel: 30,
    next: {
      level: 2,
      cost: { wood: 100, clay: 100, iron: 100 },
      surchargePercent: 0,
      pop: 2,
      durationSeconds: 900,
      effects: [],
      unlocksBuildings: [],
      unlocksUnits: [],
      ...next,
    },
    requirementsMet: true,
    missing: [],
    demolish: null,
    ...overrides,
  };
}

describe('upgradeBlocker', () => {
  it('is null when the upgrade can be queued now', () => {
    expect(upgradeBlocker(info(), village, 10, false, nowMs, true)).toBeNull();
  });

  it('is null at the maximum level: there is nothing to block', () => {
    expect(upgradeBlocker(info({ next: null }), village, 10, false, nowMs, true)).toBeNull();
  });

  it('names a queued demolition of the building before anything else (1.6)', () => {
    const demolishing = info({ level: 3, queuedLevel: 2, requirementsMet: false });
    expect(upgradeBlocker(demolishing, village, 0, true, nowMs, true)).toEqual({ kind: 'demolition' });
  });

  it('names the missing requirements first, whatever else is wrong', () => {
    const locked = info({ requirementsMet: false, missing: [{ building: 'BARRACKS', level: 5 }] });
    expect(upgradeBlocker(locked, village, 0, true, nowMs, true)).toEqual({
      kind: 'requirements',
      missing: [{ building: 'BARRACKS', level: 5 }],
    });
  });

  it('reports a full queue before the resources', () => {
    const poor = info({}, { cost: { wood: 900, clay: 900, iron: 900 } });
    expect(upgradeBlocker(poor, village, 10, true, nowMs, true)).toEqual({ kind: 'queueFull' });
  });

  it('says the warehouse is too small before the farm is looked at', () => {
    const huge = info({}, { cost: { wood: 5000, clay: 1, iron: 1 }, pop: 50 });
    expect(upgradeBlocker(huge, village, 0, false, nowMs, true)).toEqual({ kind: 'warehouse' });
  });

  it('says a resource is not produced', () => {
    const noWood = { ...village, production: { wood: 0, clay: 30, iron: 30 } };
    const needsWood = info({}, { cost: { wood: 600, clay: 1, iron: 1 } });
    expect(upgradeBlocker(needsWood, noWood, 10, false, nowMs, true)).toEqual({ kind: 'never' });
  });

  it('puts a small farm before waiting for resources', () => {
    const later = info({}, { cost: { wood: 530, clay: 1, iron: 1 }, pop: 5 });
    expect(upgradeBlocker(later, village, 4, false, nowMs, true)).toEqual({ kind: 'population' });
  });

  it('closes the Banner Hall while the world has no banners, before anything else', () => {
    const hall = info({
      building: 'BANNER_HALL',
      requirementsMet: false,
      missing: [{ building: 'BARRACKS', level: 5 }],
    });
    expect(upgradeBlocker(hall, village, 0, true, nowMs, false)).toEqual({ kind: 'bannersDisabled' });
  });

  it('leaves the Banner Hall alone while banners are on, and other buildings always', () => {
    expect(upgradeBlocker(info({ building: 'BANNER_HALL' }), village, 10, false, nowMs, true)).toBeNull();
    expect(upgradeBlocker(info(), village, 10, false, nowMs, false)).toBeNull();
  });

  it('has nothing to block at the maximum level, banners or not', () => {
    expect(upgradeBlocker(info({ building: 'BANNER_HALL', next: null }), village, 10, false, nowMs, false)).toBeNull();
  });

  it('gives the time the resources are enough', () => {
    const later = info({}, { cost: { wood: 530, clay: 1, iron: 1 } });
    // 30 more wood at 30 per hour
    expect(upgradeBlocker(later, village, 10, false, nowMs, true)).toEqual({ kind: 'later', atMs: nowMs + hour });
  });
});

describe('formatEffect', () => {
  function effect(kind: BuildingEffect['kind'], from: number | null, to: number): BuildingEffect {
    return { kind, from, to };
  }

  it('keeps a decimal for small productions and rounds down large ones', () => {
    expect(formatEffect(effect('PRODUCTION_PER_HOUR', 30, 34.95), 'en')).toEqual({ from: '30.0', to: '35.0' });
    expect(formatEffect(effect('PRODUCTION_PER_HOUR', 546.15, 636.2), 'en')).toEqual({ from: '546', to: '636' });
  });

  it('uses the thousands separator of the language', () => {
    expect(formatEffect(effect('WAREHOUSE_CAPACITY', 6397, 7862), 'en').to).toBe('7,862');
    expect(formatEffect(effect('WAREHOUSE_CAPACITY', 6397, 7862), 'de').to).toBe('7.862');
  });

  it('shows percentages of the base with at most one decimal', () => {
    expect(formatEffect(effect('BUILD_TIME_PERCENT', 95.2, 90.6304), 'en')).toEqual({ from: '95.2%', to: '90.6%' });
    expect(formatEffect(effect('WALL_DEFENSE_PERCENT', 20, 25), 'en')).toEqual({ from: '20%', to: '25%' });
    expect(formatEffect(effect('RECRUIT_TIME_PERCENT', 100, 93.7), 'de').to).toMatch(/^93,7\s%$/);
  });

  it('has no before for an effect a new building starts with', () => {
    expect(formatEffect(effect('RECRUIT_TIME_PERCENT', null, 100), 'en')).toEqual({ from: null, to: '100%' });
  });
});
