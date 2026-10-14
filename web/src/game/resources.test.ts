import { describe, expect, it } from 'vitest';
import { isFull, projectResources, whenAffordable, whenFull, type ResourceSnapshot } from './resources';

const at = '2026-10-03T12:00:00Z';
const atMs = Date.parse(at);
const hour = 3_600_000;

function snapshot(overrides: Partial<ResourceSnapshot> = {}): ResourceSnapshot {
  return {
    resources: { wood: 500, clay: 400, iron: 300, at },
    production: { wood: 30, clay: 60, iron: 90 },
    storageCapacity: 1000,
    ...overrides,
  };
}

describe('projectResources', () => {
  it('returns the stored amounts at the snapshot time', () => {
    expect(projectResources(snapshot(), atMs)).toEqual({ wood: 500, clay: 400, iron: 300 });
  });

  it('adds production per hour, fractional', () => {
    const now = atMs + hour / 2;
    expect(projectResources(snapshot(), now)).toEqual({ wood: 515, clay: 430, iron: 345 });
    const tenSeconds = projectResources(snapshot(), atMs + 10_000);
    expect(tenSeconds.wood).toBeCloseTo(500 + 30 / 360, 10);
  });

  it('stops at the storage capacity', () => {
    expect(projectResources(snapshot(), atMs + 10 * hour)).toEqual({ wood: 800, clay: 1000, iron: 1000 });
  });

  it('never reduces amounts that are already above the capacity', () => {
    const above = snapshot({ resources: { wood: 1200, clay: 1000, iron: 999.5, at } });
    expect(projectResources(above, atMs + hour)).toEqual({ wood: 1200, clay: 1000, iron: 1000 });
  });

  it('does not go backwards when the clock is behind the snapshot', () => {
    expect(projectResources(snapshot(), atMs - hour)).toEqual({ wood: 500, clay: 400, iron: 300 });
  });
});

describe('isFull', () => {
  it('compares the displayed (rounded down) amount', () => {
    expect(isFull(999.9, 1000)).toBe(false);
    expect(isFull(1000, 1000)).toBe(true);
    expect(isFull(1200, 1000)).toBe(true);
  });
});

describe('whenAffordable', () => {
  // stock 500/400/300 at `at`, production 30/60/90 per hour, warehouse 1000

  it('is "now" when the rounded-down stock covers every cost', () => {
    expect(whenAffordable(snapshot(), { wood: 500, clay: 400, iron: 300 }, atMs)).toEqual({ kind: 'now' });
    expect(whenAffordable(snapshot(), { wood: 100, clay: 100, iron: 100 }, atMs + hour)).toEqual({ kind: 'now' });
  });

  it('waits for the slowest resource', () => {
    // wood needs 1 h (30 more at 30/h), iron 2 h (180 more at 90/h)
    const result = whenAffordable(snapshot(), { wood: 530, clay: 400, iron: 480 }, atMs);
    expect(result).toEqual({ kind: 'later', atMs: atMs + 2 * hour });
  });

  it('counts from the projected stock, not the stored one', () => {
    const now = atMs + hour; // iron is at 390 by then
    expect(whenAffordable(snapshot(), { wood: 0, clay: 0, iron: 480 }, now)).toEqual({
      kind: 'later',
      atMs: now + hour,
    });
  });

  it('is never reached when a cost is above the warehouse capacity', () => {
    expect(whenAffordable(snapshot(), { wood: 1001, clay: 0, iron: 0 }, atMs)).toEqual({ kind: 'warehouse' });
  });

  it('is never reached when a missing resource is not produced', () => {
    const noIron = snapshot({ production: { wood: 30, clay: 60, iron: 0 } });
    expect(whenAffordable(noIron, { wood: 0, clay: 0, iron: 301 }, atMs)).toEqual({ kind: 'never' });
  });

  it('a fraction below the cost is not enough yet', () => {
    // 299.99 iron: the server rounds down, so 300 are not there yet
    const almost = snapshot({ resources: { wood: 500, clay: 400, iron: 299.99, at } });
    const result = whenAffordable(almost, { wood: 0, clay: 0, iron: 300 }, atMs);
    expect(result.kind).toBe('later');
  });
});

describe('whenFull', () => {
  it('says when production fills the warehouse', () => {
    // wood 500 of 1000 at 30/h: 500 / 30 = 16 h 40 min
    expect(whenFull(snapshot(), 'wood', atMs)).toEqual({ kind: 'at', atMs: atMs + Math.ceil((500 / 30) * hour) });
    // clay 400 at 60/h: 10 h
    expect(whenFull(snapshot(), 'clay', atMs)).toEqual({ kind: 'at', atMs: atMs + 10 * hour });
  });

  it('counts from the amount the stock has reached by now, not from the snapshot', () => {
    // two hours later clay is at 520: 8 h left
    expect(whenFull(snapshot(), 'clay', atMs + 2 * hour)).toEqual({ kind: 'at', atMs: atMs + 10 * hour });
  });

  it('is full once the stock reaches the capacity, and stays so above it', () => {
    expect(whenFull(snapshot(), 'iron', atMs + 8 * hour)).toEqual({ kind: 'full' });
    const above = snapshot({ resources: { wood: 1200, clay: 400, iron: 300, at } });
    expect(whenFull(above, 'wood', atMs)).toEqual({ kind: 'full' });
  });

  it('knows a resource that is not produced', () => {
    const none = snapshot({ production: { wood: 0, clay: 60, iron: 90 } });
    expect(whenFull(none, 'wood', atMs)).toEqual({ kind: 'never' });
    // but a full one is full whatever the production
    const fullButStopped = snapshot({
      resources: { wood: 1000, clay: 400, iron: 300, at },
      production: { wood: 0, clay: 0, iron: 0 },
    });
    expect(whenFull(fullButStopped, 'wood', atMs)).toEqual({ kind: 'full' });
  });
});
