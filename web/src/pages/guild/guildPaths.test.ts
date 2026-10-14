import { describe, expect, it } from 'vitest';
import { parseGuildId, guildPath } from './guildPaths';

describe('guild paths', () => {
  it('builds the path of a guild', () => {
    expect(guildPath(7)).toBe('/guilds/7');
  });

  it('reads the id back and refuses everything else', () => {
    expect(parseGuildId('7')).toBe(7);
    expect(parseGuildId('007')).toBe(7);
    for (const bad of [undefined, '', '0', '-1', '1.5', 'abc', '1e3', '1234567890123456']) {
      expect(parseGuildId(bad)).toBeUndefined();
    }
  });
});
