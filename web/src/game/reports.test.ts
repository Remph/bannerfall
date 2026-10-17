import { describe, expect, it } from 'vitest';
import {
  addShareName,
  badgeText,
  battleVerdict,
  builtBuildings,
  farmCandidate,
  farmListVillageId,
  groupByDay,
  hasSupporters,
  isScouting,
  pageCount,
  resultFor,
  shareNames,
  sharePicks,
  unitRows,
  viewerOf,
  wasSentBack,
} from './reports';

describe('resultFor', () => {
  it('sees the battle from the reader’s side', () => {
    expect(resultFor('ATTACK', 'ATTACKER_WON')).toBe('won');
    expect(resultFor('ATTACK', 'DEFENDER_WON')).toBe('lost');
    expect(resultFor('DEFENSE', 'DEFENDER_WON')).toBe('won');
    expect(resultFor('DEFENSE', 'ATTACKER_WON')).toBe('lost');
  });

  it('has no result for scouts only and supports', () => {
    expect(resultFor('SCOUT', 'SCOUTS_ONLY')).toBe('none');
    expect(resultFor('SUPPORT_ARRIVED', null)).toBe('none');
    expect(resultFor('SUPPORT_RETURNED', null)).toBe('none');
  });

  it('counts a conquest as won and a lost village as lost', () => {
    expect(resultFor('CONQUEST', null)).toBe('won');
    expect(resultFor('VILLAGE_LOST', null)).toBe('lost');
  });
});

describe('isScouting', () => {
  it('is the attacker’s SCOUT and the owner’s copy of a scouts-only attack', () => {
    expect(isScouting('SCOUT', 'SCOUTS_ONLY')).toBe(true);
    expect(isScouting('DEFENSE', 'SCOUTS_ONLY')).toBe(true);
    expect(isScouting('DEFENSE', 'ATTACKER_WON')).toBe(false);
    expect(isScouting('ATTACK', 'DEFENDER_WON')).toBe(false);
  });
});

describe('battleVerdict', () => {
  it('judges a main battle from the reader’s side', () => {
    expect(battleVerdict('ATTACK', 'ATTACKER_WON', undefined)).toEqual({ verdict: 'attackWon', result: 'won' });
    expect(battleVerdict('ATTACK', 'DEFENDER_WON', false)).toEqual({ verdict: 'attackLost', result: 'lost' });
    expect(battleVerdict('DEFENSE', 'DEFENDER_WON', undefined)).toEqual({ verdict: 'defenseWon', result: 'won' });
    expect(battleVerdict('DEFENSE', 'ATTACKER_WON', true)).toEqual({ verdict: 'defenseLost', result: 'lost' });
  });

  it('judges scouts only by whether they got through', () => {
    expect(battleVerdict('SCOUT', 'SCOUTS_ONLY', true)).toEqual({ verdict: 'scoutsThrough', result: 'won' });
    // none came back: the attacker's copy has no scouting at all
    expect(battleVerdict('SCOUT', 'SCOUTS_ONLY', undefined)).toEqual({ verdict: 'scoutsCaught', result: 'lost' });
    expect(battleVerdict('DEFENSE', 'SCOUTS_ONLY', true)).toEqual({ verdict: 'enemyScoutsThrough', result: 'lost' });
    expect(battleVerdict('DEFENSE', 'SCOUTS_ONLY', false)).toEqual({ verdict: 'enemyScoutsCaught', result: 'won' });
    // M32b: banner thieves without scouts or fighters
    expect(battleVerdict('SCOUT', 'SCOUTS_ONLY', undefined, false)).toEqual({
      verdict: 'thievesAlone',
      result: 'none',
    });
    expect(battleVerdict('DEFENSE', 'SCOUTS_ONLY', undefined, false)).toEqual({
      verdict: 'enemyThievesAlone',
      result: 'none',
    });
  });
});

describe('unitRows', () => {
  it('lists the types that were there in unit order, with what is left', () => {
    expect(unitRows({ AXEMAN: 100, SPEARMAN: 20, SCOUT: 0 }, { AXEMAN: 3, SPEARMAN: 20 })).toEqual([
      { type: 'SPEARMAN', count: 20, lost: 20, left: 0 },
      { type: 'AXEMAN', count: 100, lost: 3, left: 97 },
    ]);
  });

  it('is empty without units', () => {
    expect(unitRows({}, {})).toEqual([]);
  });
});

describe('builtBuildings', () => {
  it('keeps the buildings that exist, in building order', () => {
    expect(builtBuildings({ WALL: 5, HEADQUARTERS: 10, MARKET: 0 })).toEqual([
      ['HEADQUARTERS', 10],
      ['WALL', 5],
    ]);
  });
});

describe('badgeText', () => {
  it('shows the count, capped at 99+, and nothing for 0', () => {
    expect(badgeText(0)).toBeUndefined();
    expect(badgeText(7)).toBe('7');
    expect(badgeText(99)).toBe('99');
    expect(badgeText(100)).toBe('99+');
  });
});

describe('pageCount', () => {
  it('rounds up and is at least 1', () => {
    expect(pageCount(0, 25)).toBe(1);
    expect(pageCount(25, 25)).toBe(1);
    expect(pageCount(26, 25)).toBe(2);
  });
});

describe('wasSentBack', () => {
  it('tells who sent a support home', () => {
    // the reader's own units: the other village's owner sent them back
    expect(wasSentBack({ playerId: 7 }, 7)).toBe(true);
    // someone else's units left the reader's village: withdrawn by their owner
    expect(wasSentBack({ playerId: 8 }, 7)).toBe(false);
  });
});

describe('hasSupporters', () => {
  it('is true only when another village’s troops stood there', () => {
    expect(hasSupporters([], 1)).toBe(false);
    expect(hasSupporters([{ home: { id: 1 } }], 1)).toBe(false);
    expect(hasSupporters([{ home: { id: 1 } }, { home: { id: 2 } }], 1)).toBe(true);
    // only a support, the village itself had no units
    expect(hasSupporters([{ home: { id: 2 } }], 1)).toBe(true);
  });
});

describe('farmCandidate', () => {
  const mine: { id: number; playerId: number | null } = { id: 1, playerId: 7 };
  const theirs: { id: number; playerId: number | null } = { id: 2, playerId: 8 };
  const barbarian = { id: 3, playerId: null };

  it('offers the attacked village in the attacker’s reports', () => {
    expect(farmCandidate('ATTACK', mine, theirs, 7)).toBe(theirs);
    expect(farmCandidate('SCOUT', mine, barbarian, 7)).toBe(barbarian);
  });

  it('offers the attacker’s village in the defenders’ reports, also a supporter’s', () => {
    expect(farmCandidate('DEFENSE', theirs, mine, 7)).toBe(theirs);
    expect(farmCandidate('DEFENSE', theirs, { id: 4, playerId: 9 }, 7)).toBe(theirs);
  });

  it('never offers the reader’s own village', () => {
    expect(farmCandidate('ATTACK', theirs, mine, 7)).toBeNull();
    expect(farmCandidate('DEFENSE', mine, theirs, 7)).toBeNull();
  });

  it('offers nothing for other kinds of report', () => {
    expect(farmCandidate('SUPPORT_ARRIVED', mine, theirs, 7)).toBeNull();
    expect(farmCandidate('CONQUEST', mine, theirs, 7)).toBeNull();
  });
});

describe('farmListVillageId', () => {
  it('uses the village that sent the reader’s attack or scouts', () => {
    expect(farmListVillageId('ATTACK', 2, [1, 2], 1)).toBe(2);
    expect(farmListVillageId('SCOUT', 2, [1, 2], 1)).toBe(2);
  });

  it('falls back to the current village once the sender is lost, and in defense reports', () => {
    expect(farmListVillageId('ATTACK', 2, [1], 1)).toBe(1);
    expect(farmListVillageId('DEFENSE', 9, [1, 2], 2)).toBe(2);
    expect(farmListVillageId('DEFENSE', 9, [], undefined)).toBeUndefined();
  });
});

describe('shareNames (M40)', () => {
  it('splits on commas, semicolons and spaces and keeps each name once', () => {
    expect(shareNames(' Anna, ben;Cleo  anna\nBEN ,, ')).toEqual(['Anna', 'ben', 'Cleo']);
  });

  it('reads nothing from an empty field', () => {
    expect(shareNames('')).toEqual([]);
    expect(shareNames(' , ; ')).toEqual([]);
  });
});

describe('addShareName (M40)', () => {
  it('appends a name with a separator for the next one', () => {
    expect(addShareName('', 'Anna')).toBe('Anna, ');
    expect(addShareName('Ben', 'Anna')).toBe('Ben, Anna, ');
    expect(addShareName('Ben, ', 'Anna')).toBe('Ben, Anna, ');
  });

  it('leaves the field alone when the name is in it', () => {
    expect(addShareName('anna, Ben', 'Anna')).toBe('anna, Ben');
  });
});

describe('sharePicks (1.3.1)', () => {
  it('puts the players last sent a report to before the inbox, each once', () => {
    expect(sharePicks(['Cleo', 'Ben'], ['ben', 'Dora', 'Emil'], 6)).toEqual(['Cleo', 'Ben', 'Dora', 'Emil']);
  });

  it('stops at the maximum and works with either list empty', () => {
    expect(sharePicks(['Ben'], ['Cleo', 'Dora', 'Emil'], 3)).toEqual(['Ben', 'Cleo', 'Dora']);
    expect(sharePicks([], ['Cleo'], 6)).toEqual(['Cleo']);
    expect(sharePicks(['Ben'], [], 6)).toEqual(['Ben']);
  });
});

describe('viewerOf (M40)', () => {
  it('is the reader for a report of the game, the first owner for a copy', () => {
    expect(viewerOf(null, 7)).toBe(7);
    expect(viewerOf({ viewOfPlayerId: 3 }, 7)).toBe(3);
  });
});

describe('groupByDay', () => {
  const at = (id: number, listedAt: string) => ({ id, listedAt });

  it('puts the list’s reports under one heading per day, in list order', () => {
    const days = groupByDay(
      [
        at(5, '2026-10-16T09:00:00Z'),
        at(4, '2026-10-16T01:00:00Z'),
        at(3, '2026-10-15T22:00:00Z'),
        at(1, '2026-10-13T08:00:00Z'),
      ],
      'UTC',
    );

    expect(days.map((day) => [day.day, day.items.map((item) => item.id)])).toEqual([
      ['2026-10-16', [5, 4]],
      ['2026-10-15', [3]],
      ['2026-10-13', [1]],
    ]);
  });

  it('goes by the device’s day, and is empty without reports', () => {
    const days = groupByDay([at(2, '2026-10-16T00:30:00+02:00'), at(1, '2026-10-15T23:30:00Z')], 'Europe/Berlin');
    expect(days).toHaveLength(1);
    expect(groupByDay([])).toEqual([]);
  });
});
