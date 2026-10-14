import { describe, expect, it } from 'vitest';
import { parseTab, shortDuration, visibleTabs } from './adminTabs';

describe('visibleTabs and parseTab', () => {
  it('shows Cheats only while they are switched on', () => {
    expect(visibleTabs(false)).not.toContain('cheats');
    expect(visibleTabs(true)).toContain('cheats');
  });

  it('has a tab for the suspicion log', () => {
    expect(visibleTabs(false)).toContain('suspicious');
    expect(parseTab('suspicious', false)).toBe('suspicious');
  });

  it('falls back to Status for unknown or hidden tabs', () => {
    expect(parseTab('players', false)).toBe('players');
    expect(parseTab(null, false)).toBe('status');
    expect(parseTab('nonsense', true)).toBe('status');
    expect(parseTab('cheats', false)).toBe('status');
    expect(parseTab('cheats', true)).toBe('cheats');
  });
});

describe('shortDuration', () => {
  it('reads like the CLI', () => {
    expect(shortDuration(42)).toBe('42 s');
    expect(shortDuration(125)).toBe('2 min');
    expect(shortDuration(3 * 3600 + 5 * 60)).toBe('3 h 5 min');
    expect(shortDuration(3 * 86400 + 7200)).toBe('3 d 2 h');
  });
});
