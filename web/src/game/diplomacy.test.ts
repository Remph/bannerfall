import { describe, expect, it } from 'vitest';
import type { Diplomacy, Lord } from '../api/queries/diplomacy';
import { inviteBlock, lordState, signedTrust } from './diplomacy';

const page: Diplomacy = {
  soloMode: true,
  leader: true,
  guildFull: false,
  court: null,
  mine: null,
  allVillages: 100,
  allies: [],
  lords: [],
};

const lord: Lord = {
  playerId: 5,
  name: 'Adalbert',
  guild: null,
  ally: false,
  trust: 12,
  bar: 50,
  changes: [],
  villages: 3,
  points: 900,
  home: null,
  invitationId: null,
  inviteAfter: null,
  variant: null,
  order: null,
};

describe('inviteBlock (2.1 S2b)', () => {
  it('lets the leader of a guild with room invite in a solo world', () => {
    expect(inviteBlock(page, true)).toBeNull();
  });

  it('names the first cause only', () => {
    expect(inviteBlock({ ...page, soloMode: false, leader: false }, false)).toBe('notSolo');
    expect(inviteBlock({ ...page, leader: false }, false)).toBe('needGuild');
    expect(inviteBlock({ ...page, leader: false, guildFull: true }, true)).toBe('notLeader');
    expect(inviteBlock({ ...page, guildFull: true }, true)).toBe('guildFull');
  });
});

describe('lordState', () => {
  const now = Date.parse('2026-11-19T12:00:00Z');

  it('tells allies, invited and waiting lords apart', () => {
    expect(lordState({ ...lord, ally: true }, now)).toEqual({ kind: 'ally' });
    expect(lordState({ ...lord, invitationId: 9 }, now)).toEqual({ kind: 'invited' });
    expect(lordState({ ...lord, inviteAfter: '2026-11-20T12:00:00Z' }, now)).toEqual({
      kind: 'waiting',
      until: Date.parse('2026-11-20T12:00:00Z'),
    });
  });

  it('is open again once the wait after a no is over', () => {
    expect(lordState({ ...lord, inviteAfter: '2026-11-19T11:00:00Z' }, now)).toEqual({ kind: 'open' });
    expect(lordState(lord, now)).toEqual({ kind: 'open' });
  });
});

describe('signedTrust', () => {
  it('signs the trust with a typographic minus', () => {
    expect(signedTrust(12)).toBe('+12');
    expect(signedTrust(-8)).toBe('−8');
    expect(signedTrust(0)).toBe('0');
  });
});
