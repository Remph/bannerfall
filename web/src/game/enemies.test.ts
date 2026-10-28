import { describe, expect, it } from 'vitest';
import { enemyAction, enemySets, isEnemyPlayer, isEnemyVillage, NO_ENEMIES } from './enemies';

const list = {
  guilds: [{ id: 4 }],
  players: [{ id: 20 }],
  villages: [{ id: 300 }],
};

describe('enemy marks (1.7)', () => {
  it('has no enemies while the list is not loaded', () => {
    expect(enemySets(undefined)).toBe(NO_ENEMIES);
    expect(isEnemyPlayer(NO_ENEMIES, { id: 1, guildId: 4 })).toBe(false);
  });

  it('counts a marked player and every member of a marked guild', () => {
    const enemies = enemySets(list);
    expect(isEnemyPlayer(enemies, { id: 20, guildId: null })).toBe(true);
    expect(isEnemyPlayer(enemies, { id: 21, guildId: 4 })).toBe(true);
    expect(isEnemyPlayer(enemies, { id: 21, guildId: 5 })).toBe(false);
    expect(isEnemyPlayer(enemies, { id: 21 })).toBe(false);
  });

  it('counts a marked village, and the villages of a marked player or guild', () => {
    const enemies = enemySets(list);
    expect(isEnemyVillage(enemies, { id: 300, playerId: 99, guildId: null })).toBe(true);
    expect(isEnemyVillage(enemies, { id: 301, playerId: 20, guildId: null })).toBe(true);
    expect(isEnemyVillage(enemies, { id: 302, playerId: 21, guildId: 4 })).toBe(true);
    expect(isEnemyVillage(enemies, { id: 303, playerId: 21, guildId: 5 })).toBe(false);
    // a barbarian village is an enemy only when it is marked itself (the server refuses it anyway)
    expect(isEnemyVillage(enemies, { id: 304, playerId: null, guildId: null })).toBe(false);
  });
});

describe('enemyAction', () => {
  const mine = {
    role: 'LEADER' as const,
    guild: { id: 1, members: [{ playerId: 10 }, { playerId: 11 }] },
  };
  const base = { guildsEnabled: true, mine, marked: false };

  it('offers the leader to mark and to unmark', () => {
    expect(enemyAction({ ...base, target: { kind: 'PLAYER', id: 20 } })).toBe('mark');
    expect(enemyAction({ ...base, marked: true, target: { kind: 'GUILD', id: 4 } })).toBe('unmark');
    expect(enemyAction({ ...base, target: { kind: 'VILLAGE', id: 300, ownerId: 20 } })).toBe('mark');
  });

  it('offers nothing to a plain member, without a guild, or while guilds are off', () => {
    const target = { kind: 'PLAYER', id: 20 } as const;
    expect(enemyAction({ ...base, mine: { ...mine, role: 'MEMBER' }, target })).toBe('hidden');
    expect(enemyAction({ ...base, mine: { role: null, guild: null }, target })).toBe('hidden');
    expect(enemyAction({ ...base, mine: undefined, target })).toBe('hidden');
    expect(enemyAction({ ...base, guildsEnabled: false, target })).toBe('hidden');
  });

  it('offers nothing for what the server would refuse', () => {
    expect(enemyAction({ ...base, target: { kind: 'GUILD', id: 1 } })).toBe('hidden');
    expect(enemyAction({ ...base, target: { kind: 'PLAYER', id: 11 } })).toBe('hidden');
    expect(enemyAction({ ...base, target: { kind: 'VILLAGE', id: 300, ownerId: 10 } })).toBe('hidden');
    expect(enemyAction({ ...base, target: { kind: 'VILLAGE', id: 300, ownerId: null } })).toBe('hidden');
  });
});
