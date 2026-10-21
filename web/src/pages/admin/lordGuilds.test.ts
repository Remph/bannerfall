import { describe, expect, it } from 'vitest';
import type { NpcLord } from '../../api/queries/admin';
import { guildChoices, lordGuilds } from './lordGuilds';

function lord(playerId: number, points: number, guild: [number, string, string] | null, byAdmin = false): NpcLord {
  return {
    playerId,
    name: `Lord ${playerId}`,
    personality: 'BALANCED',
    template: 'BALANCED',
    points,
    villageCount: 1,
    lastThinkAt: null,
    nextThinkAt: null,
    braked: false,
    boosted: false,
    productionFactor: 0.9,
    guildId: guild?.[0] ?? null,
    guildName: guild?.[1] ?? null,
    guildTag: guild?.[2] ?? null,
    guildByAdmin: byAdmin,
    paladinLevel: 1,
    bannerVillages: [],
    stash: [],
    learning: null,
  };
}

const corvi: [number, string, string] = [7, 'Ordo Corvi', 'CORVI'];
const leo: [number, string, string] = [8, 'Ordo Leonis', 'LEO'];

describe('lordGuilds', () => {
  it('groups the lords by guild with their points, strongest first', () => {
    const guilds = lordGuilds([lord(1, 900, corvi), lord(2, 1000, leo), lord(3, 400, corvi), lord(4, 5000, null)]);
    expect(guilds.map((g) => [g.tag, g.lords.map((l) => l.playerId), g.points])).toEqual([
      ['CORVI', [1, 3], 1300],
      ['LEO', [2], 1000],
    ]);
  });
});

describe('guildChoices', () => {
  const first = lord(1, 900, corvi);
  const placedAlone = lord(3, 900, leo, true);
  const free = lord(4, 900, null);
  const guilds = lordGuilds([first, lord(2, 900, corvi), placedAlone, free]);

  it('offers the guilds with room, taking out, founding and letting a placed lord choose', () => {
    expect(guildChoices(free, guilds, 3).map((c) => [c.action, c.guild?.tag])).toEqual([
      ['JOIN', 'CORVI'],
      ['JOIN', 'LEO'],
      ['FOUND', undefined],
    ]);
    expect(guildChoices(first, guilds, 3).map((c) => [c.action, c.guild?.tag])).toEqual([
      ['JOIN', 'LEO'],
      ['LEAVE', undefined],
      ['FOUND', undefined],
    ]);
    // alone in its guild: founding another changes nothing
    expect(guildChoices(placedAlone, guilds, 3).map((c) => c.action)).toEqual(['JOIN', 'LEAVE', 'AUTO']);
  });

  it('leaves out full guilds and lords out of the game', () => {
    expect(guildChoices(free, guilds, 2).map((c) => c.guild?.tag)).toEqual(['LEO', undefined]);
    expect(guildChoices({ ...free, villageCount: 0 }, guilds, 3)).toEqual([]);
  });
});
