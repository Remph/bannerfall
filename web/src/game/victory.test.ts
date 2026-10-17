import { describe, expect, it } from 'vitest';
import type { Victory, VictorySide } from '../api/queries/world';
import { isMine, isPlayer, NPC_SHOWN_TOP, sameSide, shownSides, sideMeasure, sideName, villageShare } from './victory';

const guild: VictorySide = {
  kind: 'GUILD',
  id: 3,
  name: 'Knights',
  tag: 'KN',
  npc: false,
  villages: 30,
  points: 9000,
  keeps: 2,
};
const player: VictorySide = { ...guild, kind: 'PLAYER', id: 3, name: 'Ada', tag: null };

const victory: Victory = {
  mode: 'DOMINATION',
  dominationPercent: 60,
  holdHours: 48,
  keepCount: 5,
  keepPercent: 51,
  keepsNeeded: 3,
  endsAt: null,
  phase: 'OPEN',
  allVillages: 120,
  sides: [guild, player],
  leader: null,
  leaderSince: null,
  winsAt: null,
  result: null,
};

describe('victory helpers', () => {
  it('names a guild with its tag and a player alone', () => {
    expect(sideName(guild)).toBe('[KN] Knights');
    expect(sideName(player)).toBe('Ada');
  });

  it('counts the share against every village on the map', () => {
    expect(villageShare(guild, 120)).toBe(25);
    expect(villageShare(guild, 0)).toBe(0);
  });

  it('tells guilds and players with the same id apart', () => {
    expect(sameSide(guild, { ...guild })).toBe(true);
    expect(sameSide(guild, player)).toBe(false);
    expect(sameSide(guild, null)).toBe(false);
    expect(isPlayer(player, 3)).toBe(true);
    expect(isPlayer(guild, 3)).toBe(false);
  });

  it('measures by the mode while the world runs and by the reason once it has ended', () => {
    expect(sideMeasure(victory)).toBe('share');
    expect(sideMeasure({ ...victory, mode: 'KEEPS' })).toBe('keeps');
    expect(sideMeasure({ ...victory, mode: 'NONE' })).toBe('points');
    const result = { reason: 'END_DATE' as const, endedAt: '2026-11-01T12:00:00Z', winner: guild, sides: [guild] };
    expect(sideMeasure({ ...victory, phase: 'ENDED', result })).toBe('points');
    expect(sideMeasure({ ...victory, mode: 'NONE', phase: 'ENDED', result: { ...result, reason: 'KEEPS' } })).toBe(
      'keeps',
    );
  });

  it("knows the player's own side: himself or his guild, not another guild or player", () => {
    expect(isMine(player, 3, undefined)).toBe(true);
    expect(isMine(player, 4, 3)).toBe(false);
    expect(isMine(guild, 4, 3)).toBe(true);
    expect(isMine(guild, 3, undefined)).toBe(false);
    expect(isMine(guild, 4, 5)).toBe(false);
  });

  it('lists NPC lords only among the top sides', () => {
    const lord = (id: number): VictorySide => ({ ...player, id, name: `Lord ${id}`, npc: true });
    const sides = [guild, lord(10), player, lord(11), lord(12), { ...player, id: 4 }];
    const shown = shownSides(sides);
    expect(NPC_SHOWN_TOP).toBe(3);
    expect(shown.map((side) => side.name)).toEqual(['Knights', 'Lord 10', 'Ada', 'Ada']);
  });
});
