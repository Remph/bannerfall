import { describe, expect, it } from 'vitest';
import { bracketTag, charCount, nameProblem, tagProblem, guildSectionVisible, profileInvite } from './guilds';

describe('nameProblem', () => {
  it.each(['Wolves', 'abc', 'Die Wölfe', 'Iron-Wolves_2', "St. Mary's Guard", 'Ünïcödé 日本', '123', '  Padded  '])(
    'accepts %j',
    (name) => {
      expect(nameProblem(name)).toBeUndefined();
    },
  );

  it.each(['', '  ', 'ab', 'x'])('finds %j too short', (name) => {
    expect(nameProblem(name)).toBe('tooShort');
  });

  it('finds 31 characters too long and 30 fine', () => {
    expect(nameProblem('a'.repeat(31))).toBe('tooLong');
    expect(nameProblem('a'.repeat(30))).toBeUndefined();
  });

  it.each(['-Wolves', 'Wolves-', 'Two  Spaces', 'Tab\tinside', 'Wolves!', '<b>Wolves</b>'])(
    'finds %j has bad characters',
    (name) => {
      expect(nameProblem(name)).toBe('badCharacters');
    },
  );

  it('counts characters outside the BMP once, like the server', () => {
    const bold = '\u{1D400}'.repeat(30);
    expect(charCount(bold)).toBe(30);
    expect(nameProblem(bold)).toBeUndefined();
  });
});

describe('tagProblem', () => {
  it.each(['AB', 'iw', 'W0LF', '12345', ' Tag '])('accepts %j', (tag) => {
    expect(tagProblem(tag)).toBeUndefined();
  });

  it('finds the length problems', () => {
    expect(tagProblem('A')).toBe('tooShort');
    expect(tagProblem('')).toBe('tooShort');
    expect(tagProblem('ABCDEF')).toBe('tooLong');
  });

  it.each(['A B', 'A-B', 'Ä1', '日本'])('finds %j has bad characters', (tag) => {
    expect(tagProblem(tag)).toBe('badCharacters');
  });
});

describe('bracketTag', () => {
  it('puts the tag in square brackets', () => {
    expect(bracketTag('IW')).toBe('[IW]');
  });
});

describe('guildSectionVisible', () => {
  it('shows the section while guilds are on', () => {
    expect(guildSectionVisible(true, false)).toBe(true);
  });

  it('keeps it for a member when the host switched guilds off', () => {
    expect(guildSectionVisible(false, true)).toBe(true);
  });

  it('hides it for everybody else when guilds are off', () => {
    expect(guildSectionVisible(false, false)).toBe(false);
  });
});

describe('profileInvite', () => {
  const leader = {
    role: 'LEADER' as const,
    guild: { members: [1, 2, 3], invitations: [{ playerId: 40 }] },
  };
  const base = {
    guildsEnabled: true,
    mine: leader,
    maxMembers: 10,
    viewerPlayerId: 1,
    profile: { id: 7, npc: false, guild: null },
  };

  it('offers the button to a leader looking at a guildless player', () => {
    expect(profileInvite(base)).toBe('invite');
  });

  it('hides it while guilds are off, the own guild is loading, or the viewer is in no guild', () => {
    expect(profileInvite({ ...base, guildsEnabled: false })).toBe('hidden');
    expect(profileInvite({ ...base, mine: undefined })).toBe('hidden');
    expect(profileInvite({ ...base, mine: { role: null, guild: null } })).toBe('hidden');
  });

  it('hides it from plain members: only the leader invites', () => {
    expect(profileInvite({ ...base, mine: { ...leader, role: 'MEMBER' } })).toBe('hidden');
  });

  it('hides it on the own profile, for NPC lords and for players in a guild', () => {
    expect(profileInvite({ ...base, profile: { id: 1, npc: false, guild: null } })).toBe('hidden');
    expect(profileInvite({ ...base, profile: { id: 7, npc: true, guild: null } })).toBe('hidden');
    expect(profileInvite({ ...base, profile: { id: 7, npc: false, guild: { id: 3 } } })).toBe('hidden');
  });

  it('says so when the player is invited already', () => {
    expect(profileInvite({ ...base, profile: { id: 40, npc: false, guild: null } })).toBe('invited');
  });

  it('says the guild is full at the member limit, but not below it', () => {
    expect(profileInvite({ ...base, maxMembers: 3 })).toBe('full');
    expect(profileInvite({ ...base, maxMembers: 4 })).toBe('invite');
  });

  it('copes with a guild without the invitation list', () => {
    expect(profileInvite({ ...base, mine: { role: 'LEADER', guild: { members: [1], invitations: null } } })).toBe(
      'invite',
    );
  });
});
