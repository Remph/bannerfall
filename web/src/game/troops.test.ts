import { describe, expect, it } from 'vitest';
import {
  MAX_TRAIN_ATTACKS,
  canCancel,
  capCounts,
  leftForTrain,
  nextArrivalMs,
  presentUnits,
  sendableUnits,
  supportTotals,
  trainAttacks,
  travelProgress,
  troopOverview,
  unitsAway,
  withoutZeros,
} from './troops';

describe('presentUnits', () => {
  it('lists the units that are there, in unit order', () => {
    expect(presentUnits({ AXEMAN: 30, SPEARMAN: 120, SCOUT: 0, NOBLE: 1 })).toEqual([
      ['SPEARMAN', 120],
      ['AXEMAN', 30],
      ['NOBLE', 1],
    ]);
  });

  it('is empty without units', () => {
    expect(presentUnits({})).toEqual([]);
    expect(presentUnits({ RAM: 0 })).toEqual([]);
  });
});

describe('withoutZeros', () => {
  it('drops the types without units', () => {
    expect(withoutZeros({ SPEARMAN: 5, AXEMAN: 0, RAM: 2 })).toEqual({ SPEARMAN: 5, RAM: 2 });
    expect(withoutZeros({ SPEARMAN: 0 })).toEqual({});
  });
});

describe('sendableUnits', () => {
  it('shows the units at home', () => {
    expect(sendableUnits({ SWORDSMAN: 3, SPEARMAN: 10 }, {})).toEqual(['SPEARMAN', 'SWORDSMAN']);
  });

  it('keeps a row with a count although its units are gone', () => {
    expect(sendableUnits({ SPEARMAN: 10 }, { CATAPULT: 4, SCOUT: 0 })).toEqual(['SPEARMAN', 'CATAPULT']);
  });
});

describe('leftForTrain', () => {
  it('takes the attacks lined up from the units at home', () => {
    expect(leftForTrain({ AXEMAN: 100, NOBLE: 3 }, [{ AXEMAN: 60 }, { AXEMAN: 10, NOBLE: 1 }])).toEqual({
      AXEMAN: 30,
      NOBLE: 2,
    });
  });

  it('drops what is used up and never goes below 0', () => {
    expect(leftForTrain({ AXEMAN: 10, SPEARMAN: 5 }, [{ AXEMAN: 10 }, { SPEARMAN: 8 }])).toEqual({});
  });

  it('is the home units without a train', () => {
    expect(leftForTrain({ AXEMAN: 10 }, [])).toEqual({ AXEMAN: 10 });
  });
});

describe('capCounts', () => {
  it('keeps what still fits and cuts the rest', () => {
    expect(capCounts({ AXEMAN: 10, NOBLE: 1, RAM: 5 }, { AXEMAN: 4, NOBLE: 2 })).toEqual({ AXEMAN: 4, NOBLE: 1 });
  });
});

describe('trainAttacks', () => {
  it('adds the picked units as the last attack', () => {
    expect(trainAttacks([{ AXEMAN: 60 }], { NOBLE: 1, SPEARMAN: 0 })).toEqual([{ AXEMAN: 60 }, { NOBLE: 1 }]);
  });

  it('leaves out an empty pick', () => {
    expect(trainAttacks([{ AXEMAN: 60 }], { SPEARMAN: 0 })).toEqual([{ AXEMAN: 60 }]);
  });

  it('leaves out the pick when the train is full', () => {
    const full = Array.from({ length: MAX_TRAIN_ATTACKS }, () => ({ AXEMAN: 1 }));
    expect(trainAttacks(full, { NOBLE: 1 })).toEqual(full);
  });
});

describe('canCancel', () => {
  const until = '2026-10-04T12:10:00Z';

  it('allows it before cancellableUntil', () => {
    expect(canCancel({ cancellableUntil: until }, Date.parse('2026-10-04T12:09:59.999Z'))).toBe(true);
  });

  it('is over at cancellableUntil (exclusive) and after', () => {
    expect(canCancel({ cancellableUntil: until }, Date.parse(until))).toBe(false);
    expect(canCancel({ cancellableUntil: until }, Date.parse('2026-10-04T13:00:00Z'))).toBe(false);
  });

  it('never cancels a return', () => {
    expect(canCancel({ cancellableUntil: null }, 0)).toBe(false);
  });
});

describe('nextArrivalMs', () => {
  it('finds the earliest arrival', () => {
    const movements = [
      { arriveAt: '2026-10-04T12:30:00Z' },
      { arriveAt: '2026-10-04T12:05:00Z' },
      { arriveAt: '2026-10-04T13:00:00Z' },
    ];
    expect(nextArrivalMs(movements)).toBe(Date.parse('2026-10-04T12:05:00Z'));
  });

  it('is undefined without movements', () => {
    expect(nextArrivalMs([])).toBeUndefined();
  });
});

describe('unitsAway', () => {
  it('adds up stacks elsewhere, outgoing commands and returns, without incoming attacks and supports', () => {
    expect(
      unitsAway({
        ownUnitsElsewhere: [{ units: { SPEARMAN: 20 } }, { units: { SPEARMAN: 5, SCOUT: 0 } }],
        outgoing: [{ units: { AXEMAN: 50 } }],
        incoming: [
          { kind: 'RETURN', units: { AXEMAN: 10, SPEARMAN: 1 } },
          { kind: 'ATTACK', units: null },
          { kind: 'SUPPORT', units: { SPEARMAN: 40 } },
        ],
      }),
    ).toEqual({ SPEARMAN: 26, AXEMAN: 60 });
  });

  it('is empty when everything is at home', () => {
    expect(unitsAway({ ownUnitsElsewhere: [], outgoing: [], incoming: [] })).toEqual({});
  });
});

describe('troopOverview', () => {
  const empty = { home: {}, supportsHere: [], ownUnitsElsewhere: [], outgoing: [], incoming: [] };

  it('adds up home, stacks elsewhere, outgoing commands and returns', () => {
    const rows = troopOverview({
      home: { SPEARMAN: 100, AXEMAN: 0, PALADIN: 1 },
      supportsHere: [],
      ownUnitsElsewhere: [{ units: { SPEARMAN: 20 } }, { units: { SPEARMAN: 5, SCOUT: 2 } }],
      outgoing: [{ units: { AXEMAN: 50, RAM: 4 } }],
      incoming: [{ kind: 'RETURN', units: { AXEMAN: 10, LIGHT_CAVALRY: 3 } }],
    });
    expect(rows).toEqual([
      { type: 'SPEARMAN', home: 100, outside: 25, total: 125, support: 0 },
      { type: 'AXEMAN', home: 0, outside: 60, total: 60, support: 0 },
      { type: 'SCOUT', home: 0, outside: 2, total: 2, support: 0 },
      { type: 'LIGHT_CAVALRY', home: 0, outside: 3, total: 3, support: 0 },
      { type: 'RAM', home: 0, outside: 4, total: 4, support: 0 },
      { type: 'PALADIN', home: 1, outside: 0, total: 1, support: 0 },
    ]);
  });

  it('keeps support of other villages out of the total', () => {
    const rows = troopOverview({
      ...empty,
      home: { SPEARMAN: 10 },
      supportsHere: [{ units: { SPEARMAN: 30, HEAVY_CAVALRY: 8 } }, { units: { SPEARMAN: 2 } }],
    });
    expect(rows).toEqual([
      { type: 'SPEARMAN', home: 10, outside: 0, total: 10, support: 32 },
      { type: 'HEAVY_CAVALRY', home: 0, outside: 0, total: 0, support: 8 },
    ]);
  });

  it('leaves out incoming attacks and supports, which belong to other villages', () => {
    const rows = troopOverview({
      ...empty,
      incoming: [
        { kind: 'ATTACK', units: null },
        { kind: 'ATTACK', units: { AXEMAN: 100 } },
        { kind: 'SUPPORT', units: { SPEARMAN: 40 } },
      ],
    });
    expect(rows).toEqual([]);
  });

  it('is empty without troops', () => {
    expect(troopOverview(empty)).toEqual([]);
  });
});

describe('travelProgress', () => {
  const movement = { departAt: '2026-10-16T12:00:00Z', arriveAt: '2026-10-16T12:10:00Z' };

  it('runs from 0 at departure to 1 at arrival', () => {
    expect(travelProgress(movement, Date.parse('2026-10-16T12:00:00Z'))).toBe(0);
    expect(travelProgress(movement, Date.parse('2026-10-16T12:05:00Z'))).toBe(0.5);
    expect(travelProgress(movement, Date.parse('2026-10-16T12:10:00Z'))).toBe(1);
  });

  it('stays between 0 and 1, also for a movement without travel time', () => {
    expect(travelProgress(movement, Date.parse('2026-10-16T11:59:00Z'))).toBe(0);
    expect(travelProgress(movement, Date.parse('2026-10-16T12:11:00Z'))).toBe(1);
    expect(travelProgress({ departAt: movement.departAt, arriveAt: movement.departAt }, 0)).toBe(1);
  });
});

describe('supportTotals', () => {
  it('adds the support of every village up per unit type, not split by village', () => {
    expect(supportTotals([])).toEqual({});
    expect(
      supportTotals([{ units: { SPEARMAN: 30, HEAVY_CAVALRY: 8 } }, { units: { SPEARMAN: 2, ARCHER: 0 } }]),
    ).toEqual({
      SPEARMAN: 32,
      HEAVY_CAVALRY: 8,
    });
  });
});
