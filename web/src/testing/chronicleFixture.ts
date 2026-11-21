import type { Chronicle } from '../api/queries/chronicle';

/**
 * A small ended world for the chronicle's tests (2.1 I1): three villages on a 10 × 10 map, Anna (the
 * reader, id 7) takes Boris's village on day 2, the world ends on day 3.
 */
export const chronicleFixture: Chronicle = {
  endedAt: '2026-11-23T18:00:00Z',
  days: 3,
  guilds: false,
  entries: [
    {
      day: 1,
      at: '2026-11-21T09:00:00Z',
      kind: 'CONQUEST',
      mine: true,
      facts: facts({
        village: { id: 3, name: null, x: 8, y: 8 },
        keep: true,
        from: null,
        to: { id: 7, name: 'Anna', npc: false, guildId: null, guildTag: null },
        eliminated: false,
        first: true,
      }),
    },
    {
      day: 2,
      at: '2026-11-22T10:30:00Z',
      kind: 'CONQUEST',
      mine: true,
      facts: facts({
        village: { id: 2, name: 'Wulfsburg', x: 5, y: 5 },
        keep: false,
        from: { id: 8, name: 'Boris', npc: false, guildId: null, guildTag: null },
        to: { id: 7, name: 'Anna', npc: false, guildId: null, guildTag: null },
        eliminated: true,
        first: null,
      }),
    },
    {
      day: 3,
      at: '2026-11-23T18:00:00Z',
      kind: 'WORLD_ENDED',
      mine: true,
      facts: facts({
        side: { kind: 'PLAYER', id: 7, name: 'Anna', tag: null, npc: false, court: null },
        reason: 'END_DATE',
      }),
    },
  ],
  map: {
    size: 10,
    villages: [
      { x: 2, y: 2, keep: false },
      { x: 5, y: 5, keep: false },
      { x: 8, y: 8, keep: true },
    ],
    sides: [
      { kind: 'PLAYER', id: 7, name: 'Anna', tag: null, npc: false, mine: true },
      { kind: 'PLAYER', id: 8, name: 'Boris', tag: null, npc: false, mine: false },
    ],
    frames: [
      { day: 0, at: '2026-11-20T18:05:00Z', changes: [0, 1, 1, 2, 2, 0] },
      { day: 1, at: '2026-11-21T18:05:00Z', changes: [2, 1] },
      { day: 2, at: '2026-11-22T18:05:00Z', changes: [1, 1] },
      { day: 3, at: '2026-11-23T18:00:00Z', changes: [] },
    ],
  },
};

function facts(values: Partial<Chronicle['entries'][number]['facts']>): Chronicle['entries'][number]['facts'] {
  return {
    village: null,
    keep: null,
    from: null,
    to: null,
    eliminated: null,
    first: null,
    side: null,
    other: null,
    lord: null,
    player: null,
    attacked: null,
    winsAt: null,
    reason: null,
    count: null,
    topCount: null,
    ...values,
  };
}
