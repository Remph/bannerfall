import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadLossesGoal, saveLossesGoal } from './recruitLossesStorage';

function stubStorage() {
  const items = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => items.get(key) ?? null,
    setItem: (key: string, value: string) => void items.set(key, value),
    removeItem: (key: string) => void items.delete(key),
  });
  return items;
}

afterEach(() => vi.unstubAllGlobals());

describe('recruited losses goals', () => {
  it('finds the goal of a report and village, and nothing for another', () => {
    stubStorage();
    saveLossesGoal(7, 3, { AXEMAN: 90 });

    expect(loadLossesGoal(7, 3)).toEqual({ AXEMAN: 90 });
    expect(loadLossesGoal(7, 4)).toBeUndefined();
    expect(loadLossesGoal(8, 3)).toBeUndefined();
  });

  it('keeps only the newest goals', () => {
    stubStorage();
    for (let report = 1; report <= 45; report += 1) {
      saveLossesGoal(report, 3, { AXEMAN: report });
    }

    expect(loadLossesGoal(5, 3)).toBeUndefined();
    expect(loadLossesGoal(6, 3)).toEqual({ AXEMAN: 6 });
    expect(loadLossesGoal(45, 3)).toEqual({ AXEMAN: 45 });
  });

  it('survives blocked or damaged storage', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
    });

    expect(loadLossesGoal(7, 3)).toBeUndefined();
    expect(() => saveLossesGoal(7, 3, { AXEMAN: 1 })).not.toThrow();

    const items = stubStorage();
    items.set('bannerfall.recruitLossesGoals', 'not json');
    expect(loadLossesGoal(7, 3)).toBeUndefined();
  });
});
