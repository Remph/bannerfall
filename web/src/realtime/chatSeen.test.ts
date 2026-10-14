import { describe, expect, it } from 'vitest';
import { isChatSeenKey, parseSeenId, storageKey } from './chatSeen';

describe('parseSeenId', () => {
  it('reads a stored id', () => {
    expect(parseSeenId('42')).toBe(42);
    expect(parseSeenId('0')).toBe(0);
  });

  it('ignores nothing or garbage', () => {
    expect(parseSeenId(null)).toBeNull();
    expect(parseSeenId('')).toBeNull();
    expect(parseSeenId('-3')).toBeNull();
    expect(parseSeenId('abc')).toBeNull();
  });
});

describe('isChatSeenKey', () => {
  it('reacts to the seen ids of any player and to a cleared storage', () => {
    expect(isChatSeenKey('bannerfall.chatSeen.7')).toBe(true);
    expect(isChatSeenKey('bannerfall.guildChatSeen.7.3')).toBe(true);
    expect(isChatSeenKey(null)).toBe(true);
    expect(isChatSeenKey('bannerfall.chatDock')).toBe(false);
  });
});

describe('storageKey', () => {
  it('keeps one seen id per guild, so a new guild starts afresh', () => {
    expect(storageKey(7)).toBe('bannerfall.chatSeen.7');
    expect(storageKey(7, 3)).toBe('bannerfall.guildChatSeen.7.3');
    expect(storageKey(7, 3)).not.toBe(storageKey(7, 4));
    expect(isChatSeenKey(storageKey(7, 3))).toBe(true);
  });
});
