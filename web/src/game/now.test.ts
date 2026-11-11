import { describe, expect, it } from 'vitest';
import type { Now, NowVillage } from '../api/queries/now';
import { hostileCount, isHostile, nextNowEndMs, nowSections, orderProgress } from './now';

const ref = (id: number, name = `Dorf ${id}`) => ({ id, name, x: id, y: id, playerId: 1, playerName: 'Me' });

function village(id: number, over: Partial<NowVillage> = {}): NowVillage {
  return {
    village: ref(id),
    builds: [],
    training: [],
    dismissals: [],
    research: [],
    outgoing: [],
    incoming: [],
    merchants: [],
    ...over,
  };
}

const build = (id: number, endAt: string) =>
  ({
    id,
    building: 'FARM',
    targetLevel: 2,
    startAt: '2026-11-11T10:00:00Z',
    endAt,
    demolish: false,
    automated: false,
  }) as const;

const train = (id: number, building: 'BARRACKS' | 'STABLE', endAt: string) =>
  ({
    id,
    building,
    unit: 'SPEARMAN',
    count: 10,
    completedCount: 0,
    secondsPerUnit: 60,
    startAt: '2026-11-11T10:00:00Z',
    endAt,
  }) as const;

const attack = (id: number, arriveAt: string) =>
  ({
    id,
    kind: 'ATTACK',
    sourceVillage: ref(99),
    sourcePlayerName: 'Foe',
    departAt: null,
    arriveAt,
    units: null,
  }) as const;

const support = (id: number, arriveAt: string) =>
  ({
    id,
    kind: 'SUPPORT',
    sourceVillage: ref(98),
    sourcePlayerName: 'Friend',
    departAt: '2026-11-11T09:00:00Z',
    arriveAt,
    units: { SPEARMAN: 5 },
  }) as const;

describe('nowSections', () => {
  const now: Now = {
    villages: [
      village(1, {
        builds: [build(1, '2026-11-11T10:10:00Z'), build(2, '2026-11-11T10:30:00Z')],
        training: [train(3, 'BARRACKS', '2026-11-11T10:20:00Z'), train(4, 'BARRACKS', '2026-11-11T10:40:00Z')],
        incoming: [attack(5, '2026-11-11T10:50:00Z'), support(6, '2026-11-11T10:15:00Z')],
      }),
      village(2, {
        builds: [build(7, '2026-11-11T10:05:00Z')],
        training: [train(8, 'STABLE', '2026-11-11T10:25:00Z')],
        incoming: [attack(9, '2026-11-11T10:45:00Z')],
        merchants: [
          {
            id: 10,
            sourceVillage: ref(2),
            targetVillage: ref(1),
            resources: { wood: 1, clay: 0, iron: 0 },
            merchants: 1,
            departAt: '2026-11-11T09:50:00Z',
            arriveAt: '2026-11-11T10:08:00Z',
            returnAt: '2026-11-11T10:30:00Z',
            arrived: true,
          },
        ],
      }),
    ],
  };

  it('shows only the current village in the "this" scope, but every attack', () => {
    const sections = nowSections(now, 'this', 1);
    expect(sections.building.map((row) => row.key)).toEqual(['build-1', 'build-2']);
    // one running order per building, the second waits behind it
    expect(sections.training.map((row) => row.key)).toEqual(['train-3']);
    expect(sections.training[0]).toMatchObject({ kind: 'train', waiting: 1 });
    // attacks on the other village stay visible, soonest first
    expect(sections.attacks.map((row) => row.key)).toEqual(['attack-9', 'attack-5']);
    expect(sections.way.map((row) => row.key)).toEqual(['incoming-6']);
  });

  it('covers all villages in the "all" scope, sorted by time', () => {
    const sections = nowSections(now, 'all', 1);
    expect(sections.building.map((row) => row.key)).toEqual(['build-7', 'build-1', 'build-2']);
    expect(sections.training.map((row) => row.key)).toEqual(['train-3', 'train-8']);
    // returning merchants are due when they are home
    expect(sections.way.map((row) => row.key)).toEqual(['incoming-6', 'merchants-10']);
    expect(sections.count).toBe(2 + 3 + 2 + 2);
  });

  it('shows every village when none is current', () => {
    expect(nowSections(now, 'this', undefined).building).toHaveLength(3);
  });

  it('treats a visible-units attack as a normal line on the way', () => {
    const own = { ...attack(11, '2026-11-11T11:00:00Z'), units: { AXEMAN: 3 } };
    const sections = nowSections({ villages: [village(1, { incoming: [own] })] }, 'all', 1);
    expect(sections.attacks).toEqual([]);
    expect(sections.way.map((row) => row.key)).toEqual(['incoming-11']);
    expect(isHostile(own)).toBe(false);
  });

  it('lists a support between two own villages once, as the sender’s line, when both are in scope', () => {
    const sent = {
      id: 12,
      kind: 'SUPPORT',
      sourceVillage: ref(1),
      targetVillage: ref(2),
      homeVillage: ref(1),
      units: { SPEARMAN: 5 },
      catapultTarget: null,
      loot: { wood: 0, clay: 0, iron: 0 },
      departAt: '2026-11-11T10:00:00Z',
      arriveAt: '2026-11-11T11:00:00Z',
      cancellableUntil: null,
      notifyReturn: false,
    } as const;
    const arriving = { ...support(12, '2026-11-11T11:00:00Z'), sourceVillage: ref(1), sourcePlayerName: 'Me' };
    const both: Now = {
      villages: [village(1, { outgoing: [sent] }), village(2, { incoming: [arriving] })],
    };
    expect(nowSections(both, 'all', 1).way.map((row) => row.key)).toEqual(['outgoing-12']);
    expect(nowSections(both, 'all', 1).count).toBe(1);
    // looking at the receiving village alone, it is that village's arrival
    expect(nowSections(both, 'this', 2).way.map((row) => row.key)).toEqual(['incoming-12']);
    expect(nowSections(both, 'this', 1).way.map((row) => row.key)).toEqual(['outgoing-12']);
  });

  it('is empty when nothing runs', () => {
    expect(nowSections({ villages: [village(1)] }, 'all', 1)).toEqual({
      attacks: [],
      building: [],
      training: [],
      way: [],
      count: 0,
    });
  });
});

describe('hostileCount', () => {
  it('counts attacks on all villages', () => {
    const now: Now = {
      villages: [
        village(1, { incoming: [attack(1, '2026-11-11T10:00:00Z')] }),
        village(2, { incoming: [support(2, '2026-11-11T10:00:00Z'), attack(3, '2026-11-11T10:00:00Z')] }),
      ],
    };
    expect(hostileCount(now)).toBe(2);
  });
});

describe('nextNowEndMs', () => {
  it('is the earliest end over everything', () => {
    const now: Now = {
      villages: [
        village(1, { builds: [build(1, '2026-11-11T10:10:00Z')] }),
        village(2, { incoming: [support(2, '2026-11-11T10:02:00Z')] }),
      ],
    };
    expect(nextNowEndMs(now)).toBe(Date.parse('2026-11-11T10:02:00Z'));
  });

  it('is undefined while nothing runs', () => {
    expect(nextNowEndMs({ villages: [village(1)] })).toBeUndefined();
  });
});

describe('orderProgress', () => {
  const order = { startAt: '2026-11-11T10:00:00Z', endAt: '2026-11-11T11:00:00Z' };
  it('runs from 0 to 1', () => {
    expect(orderProgress(order, Date.parse('2026-11-11T09:00:00Z'))).toBe(0);
    expect(orderProgress(order, Date.parse('2026-11-11T10:30:00Z'))).toBe(0.5);
    expect(orderProgress(order, Date.parse('2026-11-11T12:00:00Z'))).toBe(1);
  });
});
