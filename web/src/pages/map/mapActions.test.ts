import { describe, expect, it } from 'vitest';
import { mapActions, rankActions } from './mapActions';

const keys = (actions: { key: string }[]) => actions.map((action) => action.key);

describe('mapActions', () => {
  it('opens the current village, nothing else', () => {
    expect(mapActions({ id: 1, playerId: null, x: 10, y: 10 }, 'own', 1)).toEqual([{ key: 'open', to: '/v/1' }]);
  });

  it('opens, supports and trades with another own village', () => {
    const actions = mapActions({ id: 2, playerId: null, x: 12, y: 14 }, 'own', 1);
    expect(keys(actions)).toEqual(['open', 'support', 'sendResources']);
    expect(actions[0]).toEqual({ key: 'open', to: '/v/2' });
    expect(actions[1]).toEqual({ key: 'support', to: '/v/1/rally?x=12&y=14' });
  });

  it('offers attack, farm list, support, trade and the profile for players and NPC lords', () => {
    expect(keys(mapActions({ id: 3, playerId: null, x: 0, y: 0 }, 'player', 1))).toEqual([
      'attack',
      'farm',
      'support',
      'sendResources',
      'profile',
    ]);
    expect(keys(mapActions({ id: 4, playerId: null, x: 0, y: 0 }, 'npc', 1))).toEqual([
      'attack',
      'farm',
      'support',
      'sendResources',
      'profile',
    ]);
  });

  it('only attacks barbarians, or puts them on the farm list', () => {
    expect(mapActions({ id: 5, playerId: null, x: 48, y: 52 }, 'barbarian', 1)).toEqual([
      { key: 'attack', to: '/v/1/rally?x=48&y=52' },
      { key: 'farm', to: '/v/1/farm?x=48&y=52' },
    ]);
  });

  it('adds a village to the farm list of the current village, target filled in', () => {
    const actions = mapActions({ id: 3, playerId: null, x: 48, y: 52 }, 'player', 7);
    expect(actions.find((action) => action.key === 'farm')?.to).toBe('/v/7/farm?x=48&y=52');
  });

  it('sends attacks and supports from the current village to the rally point, target filled in', () => {
    const actions = mapActions({ id: 3, playerId: null, x: 48, y: 52 }, 'npc', 7);
    expect(actions.find((action) => action.key === 'attack')?.to).toBe('/v/7/rally?x=48&y=52');
    expect(actions.find((action) => action.key === 'support')?.to).toBe('/v/7/rally?x=48&y=52');
  });

  it('opens the market from the current village with the target filled in', () => {
    const own = mapActions({ id: 2, playerId: null, x: 12, y: 14 }, 'own', 1);
    expect(own.find((action) => action.key === 'sendResources')?.to).toBe('/v/1/market?x=12&y=14');
    const other = mapActions({ id: 3, playerId: null, x: 48, y: 52 }, 'player', 7);
    expect(other.find((action) => action.key === 'sendResources')?.to).toBe('/v/7/market?x=48&y=52');
  });

  it("links the owner's profile for players and NPC lords", () => {
    for (const kind of ['player', 'npc'] as const) {
      const profile = mapActions({ id: 3, playerId: 9, x: 0, y: 0 }, kind, 1).find(
        (action) => action.key === 'profile',
      );
      expect(profile?.to).toBe('/players/9');
    }
  });

  it('has no profile link without an owner id', () => {
    const profile = mapActions({ id: 3, playerId: null, x: 0, y: 0 }, 'player', 1).find(
      (action) => action.key === 'profile',
    );
    expect(profile?.to).toBeUndefined();
  });
});

describe('rankActions', () => {
  const ranked = (kind: Parameters<typeof mapActions>[1], id = 3) => {
    const { lead, second, quiet } = rankActions(mapActions({ id, playerId: 9, x: 0, y: 0 }, kind, 1));
    return { lead: lead?.key, second: second?.key, quiet: keys(quiet) };
  };

  it('leads with attack on players and NPC lords, support second, the rest quiet', () => {
    const expected = { lead: 'attack', second: 'support', quiet: ['farm', 'sendResources', 'profile'] };
    expect(ranked('player')).toEqual(expected);
    expect(ranked('npc')).toEqual(expected);
  });

  it('puts the farm list next to the attack on barbarians', () => {
    expect(ranked('barbarian')).toEqual({ lead: 'attack', second: 'farm', quiet: [] });
  });

  it('leads with opening an own village', () => {
    expect(ranked('own')).toEqual({ lead: 'open', second: 'support', quiet: ['sendResources'] });
    expect(ranked('own', 1)).toEqual({ lead: 'open', second: undefined, quiet: [] });
  });
});
