import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { endScreenDue, loadWorldEndSeen, saveWorldEndSeen } from './worldEndSeen';

const END = '2026-11-01T18:00:00Z';

describe('endScreenDue', () => {
  it('shows the end screen once per end of the world', () => {
    expect(endScreenDue(END, null)).toBe(true);
    expect(endScreenDue(END, END)).toBe(false);
    // reopened and ended again
    expect(endScreenDue('2026-11-03T09:00:00Z', END)).toBe(true);
  });
});

describe('the stored end', () => {
  // the tests run in node: a tiny stand-in for the browser's storage
  beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('remembers the end the screen was closed for', () => {
    expect(loadWorldEndSeen()).toBeNull();
    saveWorldEndSeen(END);
    expect(loadWorldEndSeen()).toBe(END);
  });

  it('shows the screen again when the storage is blocked', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
    });
    expect(loadWorldEndSeen()).toBeNull();
    expect(() => saveWorldEndSeen(END)).not.toThrow();
  });
});
