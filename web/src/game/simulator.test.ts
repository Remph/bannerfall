import { describe, expect, it } from 'vitest';
import type { BattleReport, ReportItem } from '../api/queries/reports';
import {
  MAX_SIMULATED_LEVEL,
  SIMULATOR_REPORT_CHOICES,
  buildingMaxLevels,
  canSimulate,
  emptyForm,
  filledTypes,
  reportToForm,
  simulatableReports,
  simulatedMaxLevel,
  sumUnits,
  toRequest,
  withBannerTier,
  withCatapultTarget,
} from './simulator';

const village = { id: 1, name: 'A', x: 1, y: 1, playerId: 1, playerName: 'P' };

/** A battle report with the fields the form uses; the rest is filled with neutral values. */
function battle(overrides: Partial<BattleReport>): BattleReport {
  return {
    type: 'BATTLE',
    attacker: village,
    defender: village,
    outcome: 'ATTACKER_WON',
    luck: 0.05,
    attackerUnits: { AXEMAN: 100, RAM: 5 },
    attackerLosses: { AXEMAN: 30 },
    catapultTarget: null,
    defenderSide: {
      units: { SPEARMAN: 80, SCOUT: 2 },
      losses: { SPEARMAN: 80 },
      stacks: [],
      wallBefore: 3,
      wallAfter: 1,
      catapultLevelBefore: null,
      catapultLevelAfter: null,
    },
    scouting: null,
    loot: { wood: 0, clay: 0, iron: 0 },
    loyalty: null,
    ...overrides,
  } as BattleReport;
}

describe('toRequest', () => {
  it('drops zero counts and sends the catapult target only with catapults', () => {
    const body = toRequest({
      ...emptyForm,
      attacker: { AXEMAN: 10, RAM: 0 },
      defender: { SPEARMAN: 5 },
      catapultTarget: 'SMITHY',
      catapultTargetLevel: 4,
      loyalty: 40,
    });
    expect(body).toEqual({
      attacker: { AXEMAN: 10 },
      defender: { SPEARMAN: 5 },
      attackerLevels: null,
      defenderLevels: null,
      wall: 0,
      catapultTarget: null,
      catapultTargetLevel: null,
      loyalty: null,
      warTier: null,
      bulwarkTier: null,
      bannerHall: null,
      plunderTier: null,
      oathTier: null,
      attackerStandard: null,
      attackerStandardTier: null,
      defenderStandard: null,
      defenderStandardTier: null,
      night: null,
      morale: null,
      guildBannerLevel: null,
    });
  });

  it('sends morale below 100 only, an empty field as full strength, never below the lowest (1.6)', () => {
    const attack = { ...emptyForm, attacker: { AXEMAN: 1 } };
    expect(toRequest(attack).morale).toBeNull();
    expect(toRequest({ ...attack, morale: 48 }).morale).toBe(48);
    expect(toRequest({ ...attack, morale: 0 }).morale).toBeNull();
    expect(toRequest({ ...attack, morale: 4 }).morale).toBe(30);
  });

  it('sends the guild banner level only above 0 (1.7)', () => {
    const attack = { ...emptyForm, attacker: { AXEMAN: 1 } };
    expect(toRequest(attack).guildBannerLevel).toBeNull();
    expect(toRequest({ ...attack, guildBannerLevel: 3 }).guildBannerLevel).toBe(3);
  });

  it('sends a paladin standard only with a paladin on that side (1.4)', () => {
    const form = {
      ...emptyForm,
      attacker: { AXEMAN: 10, PALADIN: 1 },
      defender: { SPEARMAN: 10 },
      attackerStandard: 'AXE_STANDARD' as const,
      attackerStandardTier: 2,
      defenderStandard: 'SPEAR_STANDARD' as const,
      defenderStandardTier: 3,
    };
    expect(toRequest(form)).toMatchObject({
      attackerStandard: 'AXE_STANDARD',
      attackerStandardTier: 2,
      defenderStandard: null,
      defenderStandardTier: null,
    });
    expect(toRequest({ ...form, attackerStandardTier: 0 })).toMatchObject({ attackerStandard: null });
  });

  it('sends the banners and the hall only when set (M32c)', () => {
    const body = toRequest({ ...emptyForm, attacker: { AXEMAN: 10 }, warTier: 2, bulwarkTier: 3, bannerHall: 1 });
    expect(body).toMatchObject({ warTier: 2, bulwarkTier: 3, bannerHall: 1 });
  });

  it('sends Plunder and Oath only when set (D4)', () => {
    const body = toRequest({ ...emptyForm, attacker: { AXEMAN: 10 }, plunderTier: 1, oathTier: 2, bannerHall: 2 });
    expect(body).toMatchObject({ warTier: null, bulwarkTier: null, plunderTier: 1, oathTier: 2, bannerHall: 2 });
  });

  it('sends the unit levels above 1 and nothing for level 1 (M30)', () => {
    const body = toRequest({
      ...emptyForm,
      attacker: { AXEMAN: 10 },
      attackerLevels: { AXEMAN: 3, SPEARMAN: 1 },
      defenderLevels: { SPEARMAN: 2 },
    });
    expect(body.attackerLevels).toEqual({ AXEMAN: 3 });
    expect(body.defenderLevels).toEqual({ SPEARMAN: 2 });
  });

  it('sends the target level of catapults, but not for the wall (its level is the wall field)', () => {
    const form = { ...emptyForm, attacker: { CATAPULT: 20, NOBLE: 1 }, catapultTarget: 'SMITHY' as const };
    expect(toRequest({ ...form, catapultTargetLevel: 4, loyalty: 70 })).toMatchObject({
      catapultTarget: 'SMITHY',
      catapultTargetLevel: 4,
      loyalty: 70,
    });
    expect(toRequest({ ...form, catapultTarget: 'WALL', catapultTargetLevel: 4 })).toMatchObject({
      catapultTarget: 'WALL',
      catapultTargetLevel: null,
    });
  });
});

describe('simulatedMaxLevel', () => {
  const maxLevels = buildingMaxLevels([
    { building: 'WALL', maxLevel: 20 },
    { building: 'RALLY_POINT', maxLevel: 1 },
    { building: 'BANNER_HALL', maxLevel: 3 },
  ]);

  it('stops at the building’s own maximum', () => {
    expect(simulatedMaxLevel(maxLevels, 'WALL')).toBe(20);
    expect(simulatedMaxLevel(maxLevels, 'RALLY_POINT')).toBe(1);
    expect(simulatedMaxLevel(maxLevels, 'BANNER_HALL')).toBe(3);
  });

  it('falls back to the general caps while the building list is loading', () => {
    expect(simulatedMaxLevel(buildingMaxLevels(undefined), 'HEADQUARTERS')).toBe(MAX_SIMULATED_LEVEL);
    expect(simulatedMaxLevel({}, 'BANNER_HALL')).toBe(3);
  });
});

describe('withCatapultTarget', () => {
  it('forgets the level of the previous target', () => {
    const form = { ...emptyForm, catapultTarget: 'HEADQUARTERS' as const, catapultTargetLevel: 20 };
    expect(withCatapultTarget(form, 'RALLY_POINT')).toMatchObject({
      catapultTarget: 'RALLY_POINT',
      catapultTargetLevel: 0,
    });
  });

  it('keeps the level when the same target is picked again', () => {
    const form = { ...emptyForm, catapultTargetLevel: 20 };
    expect(withCatapultTarget(form, form.catapultTarget)).toBe(form);
  });
});

describe('canSimulate', () => {
  it('needs an attacking unit', () => {
    expect(canSimulate(emptyForm)).toBe(false);
    expect(canSimulate({ ...emptyForm, attacker: { SPEARMAN: 0 } })).toBe(false);
    expect(canSimulate({ ...emptyForm, attacker: { SCOUT: 1 } })).toBe(true);
  });
});

/** A report's banner part without the paladin standards of 1.4. */
const noStandards = { attackStandard: null, foundStandard: null, standardTheft: null, guildDefenceBonus: null };

describe('reportToForm with banners (M32c)', () => {
  const effects = { WAR: [0.03, 0.06, 0.1], BULWARK: [0.03, 0.06, 0.1] };

  it('takes the War banner the attack took along and the scouted Bulwark and hall', () => {
    const form = reportToForm(
      battle({
        banners: { attackBonus: 0.06, defenceBonus: 0.03, found: null, theft: null, stolen: null, ...noStandards },
        scouting: {
          succeeded: true,
          resources: null,
          buildings: { WALL: 3, BANNER_HALL: 1 },
          banner: { type: 'BULWARK', tier: 3 },
          away: null,
          standard: null,
        },
      }),
      effects,
    );
    expect(form).toMatchObject({ warTier: 2, bulwarkTier: 3, bannerHall: 1 });
  });

  it('a successful scouting without a Bulwark means none; the defenders copy names the one that worked', () => {
    const scouted = reportToForm(
      battle({
        scouting: {
          succeeded: true,
          resources: null,
          buildings: { BANNER_HALL: 2 },
          banner: { type: 'WAR', tier: 1 },
          away: null,
          standard: null,
        },
      }),
      effects,
    );
    expect(scouted).toMatchObject({ warTier: 0, bulwarkTier: 0, bannerHall: 2 });
    const defence = reportToForm(
      battle({
        banners: { attackBonus: null, defenceBonus: 0.06, found: null, theft: null, stolen: null, ...noStandards },
      }),
      effects,
    );
    expect(defence).toMatchObject({ bulwarkTier: 2, bannerHall: 2 });
  });

  it('takes a scouted Oath banner; a named War or Bulwark clears the other banner of that side (D4)', () => {
    const oath = reportToForm(
      battle({
        scouting: {
          succeeded: true,
          resources: null,
          buildings: { BANNER_HALL: 3 },
          banner: { type: 'OATH', tier: 2 },
          away: null,
          standard: null,
        },
      }),
      effects,
    );
    expect(oath).toMatchObject({ oathTier: 2, bulwarkTier: 0, bannerHall: 3 });
    const named = reportToForm(
      battle({
        banners: { attackBonus: 0.06, defenceBonus: 0.06, found: null, theft: null, stolen: null, ...noStandards },
      }),
      effects,
    );
    expect(named).toMatchObject({ warTier: 2, plunderTier: 0, bulwarkTier: 2, oathTier: 0 });
  });

  it('leaves the banners out without the effect table', () => {
    const form = reportToForm(
      battle({
        banners: { attackBonus: 0.06, defenceBonus: null, found: null, theft: null, stolen: null, ...noStandards },
      }),
    );
    expect(form.warTier).toBeUndefined();
    expect(form.bulwarkTier).toBeUndefined();
  });
});

describe('reportToForm', () => {
  it('takes both armies, the wall and the loyalty before an attack', () => {
    const form = reportToForm(battle({ loyalty: { before: 80, after: 45, conquered: false } }));
    expect(form).toEqual({
      attacker: { AXEMAN: 100, RAM: 5 },
      defender: { SPEARMAN: 80, SCOUT: 2 },
      attackerLevels: {},
      defenderLevels: {},
      wall: 3,
      loyalty: 80,
      night: false,
      morale: 100,
    });
  });

  it('ticks the night switch for a battle with the night bonus, not for a scouting (1.4)', () => {
    expect(reportToForm(battle({ nightBonus: 2 })).night).toBe(true);
    expect(reportToForm(battle({ nightBonus: null })).night).toBe(false);
    expect(reportToForm(battle({ outcome: 'SCOUTS_ONLY', nightBonus: null })).night).toBeUndefined();
    expect(toRequest({ ...emptyForm, attacker: { AXEMAN: 1 }, night: true }).night).toBe(true);
  });

  it('takes the morale of a battle that had it, full strength otherwise (1.6)', () => {
    expect(reportToForm(battle({ morale: 0.48 })).morale).toBe(48);
    expect(reportToForm(battle({ morale: null })).morale).toBe(100);
    expect(reportToForm(battle({ outcome: 'SCOUTS_ONLY', morale: null })).morale).toBeUndefined();
  });

  it('takes the unit levels of that battle; the defender gets the highest level of its stacks (M30)', () => {
    const stack = (levels: Record<string, number>) => ({
      home: village,
      units: { SPEARMAN: 10 },
      losses: {},
      levels,
      standard: null,
    });
    const form = reportToForm(
      battle({
        attackerLevels: { AXEMAN: 2 },
        defenderSide: {
          units: { SPEARMAN: 20 },
          losses: {},
          stacks: [stack({ SPEARMAN: 2 }), stack({ SPEARMAN: 3 })],
          wallBefore: 0,
          wallAfter: 0,
          catapultLevelBefore: null,
          catapultLevelAfter: null,
          levels: {},
        },
      }),
    );
    expect(form.attackerLevels).toEqual({ AXEMAN: 2 });
    expect(form.defenderLevels).toEqual({ SPEARMAN: 3 });
  });

  it('takes the defender levels that upgraded scouts saw (the attacker has no stacks)', () => {
    const form = reportToForm(
      battle({
        defenderSide: {
          units: { SPEARMAN: 20 },
          losses: {},
          stacks: [],
          wallBefore: 0,
          wallAfter: 0,
          catapultLevelBefore: null,
          catapultLevelAfter: null,
          levels: { SPEARMAN: 3 },
        },
      }),
    );
    expect(form.defenderLevels).toEqual({ SPEARMAN: 3 });
  });

  it('takes the loyalty from before a conquering battle (after it the village is at 0)', () => {
    const form = reportToForm(battle({ loyalty: { before: 31, after: 0, conquered: true } }));
    expect(form.loyalty).toBe(31);
  });

  it('takes the catapult target and its level', () => {
    const form = reportToForm(
      battle({
        catapultTarget: 'SMITHY',
        defenderSide: {
          units: {},
          losses: {},
          stacks: [],
          wallBefore: 0,
          wallAfter: 0,
          levels: {},
          catapultLevelBefore: 7,
          catapultLevelAfter: 5,
        },
      }),
    );
    expect(form.catapultTarget).toBe('SMITHY');
    expect(form.catapultTargetLevel).toBe(7);
  });

  it('keeps the attacker of the form for a spy report and prefers the scouted wall level', () => {
    const form = reportToForm(
      battle({
        outcome: 'SCOUTS_ONLY',
        luck: null,
        attackerUnits: { SCOUT: 3 },
        scouting: {
          succeeded: true,
          resources: null,
          buildings: { WALL: 8, HEADQUARTERS: 12 },
          banner: null,
          away: null,
          standard: null,
        },
      }),
    );
    expect(form.attacker).toBeUndefined();
    expect(form.defender).toEqual({ SPEARMAN: 80, SCOUT: 2 });
    expect(form.wall).toBe(8);
    expect(form.catapultTargetLevel).toBe(12);
  });

  it('leaves the defender alone when nobody came back to report it', () => {
    const form = reportToForm(battle({ outcome: 'DEFENDER_WON', defenderSide: null }));
    expect(form.attacker).toEqual({ AXEMAN: 100, RAM: 5 });
    expect(form.defender).toBeUndefined();
    expect(form.wall).toBeUndefined();
  });
});

describe('sumUnits', () => {
  it('adds stacks and ignores zeros', () => {
    expect(sumUnits({ SPEARMAN: 10, AXEMAN: 0 }, { SPEARMAN: 5, SCOUT: 1 })).toEqual({ SPEARMAN: 15, SCOUT: 1 });
  });
});

describe('simulatableReports', () => {
  it('keeps the battle reports only, newest first, and finds them behind trade reports', () => {
    // 40 trade reports newer than the battles: a page of the 25 newest reports would hold no battle
    const trades = Array.from({ length: 40 }, (_, id) => ({ id, kind: 'TRADE_ARRIVED' as const }));
    const battles = Array.from({ length: 30 }, (_, id) => ({
      id: 100 + id,
      kind: (['ATTACK', 'DEFENSE', 'SCOUT'] as const)[id % 3] ?? 'ATTACK',
    }));
    const items: { id: number; kind: ReportItem['kind'] }[] = [
      ...trades,
      { id: 99, kind: 'CONQUEST' as const },
      ...battles,
    ];

    const choices = simulatableReports(items);

    expect(choices).toHaveLength(SIMULATOR_REPORT_CHOICES);
    expect(choices.map((item) => item.id)).toEqual(battles.slice(0, SIMULATOR_REPORT_CHOICES).map((item) => item.id));
  });
});

describe('withBannerTier (D4)', () => {
  it('a banner clears the other one of its side, not the other side', () => {
    const form = { ...emptyForm, warTier: 2, bulwarkTier: 3 };
    expect(withBannerTier(form, 'plunderTier', 1)).toMatchObject({ warTier: 0, plunderTier: 1, bulwarkTier: 3 });
    expect(withBannerTier(form, 'oathTier', 2)).toMatchObject({ warTier: 2, bulwarkTier: 0, oathTier: 2 });
  });

  it('setting none clears nothing else', () => {
    const form = { ...emptyForm, warTier: 2 };
    expect(withBannerTier(form, 'plunderTier', 0)).toMatchObject({ warTier: 2, plunderTier: 0 });
  });
});

describe('reportToForm with paladin standards (1.4)', () => {
  it('takes the standard the attack carried and the one the scouts saw', () => {
    const form = reportToForm(
      battle({
        banners: {
          attackBonus: null,
          guildDefenceBonus: null,
          defenceBonus: null,
          found: null,
          theft: null,
          stolen: null,
          attackStandard: { type: 'AXE_STANDARD', tier: 3, effectiveTier: 1 },
          foundStandard: null,
          standardTheft: null,
        },
        scouting: {
          succeeded: true,
          resources: null,
          buildings: { BANNER_HALL: 1 },
          banner: null,
          away: null,
          standard: { type: 'SPEAR_STANDARD', tier: 2 },
        },
      }),
    );
    expect(form).toMatchObject({
      attackerStandard: 'AXE_STANDARD',
      attackerStandardTier: 3,
      defenderStandard: 'SPEAR_STANDARD',
      defenderStandardTier: 2,
    });
  });
});

/** 1.6 UI review: after a fill-in a phone lists only the units it got. */
describe('filledTypes', () => {
  it('lists the units with a count, or null for an empty side', () => {
    expect(filledTypes({ AXEMAN: 200, SPEARMAN: 0, RAM: 5 })).toEqual(['AXEMAN', 'RAM']);
    expect(filledTypes({ SPEARMAN: 0 })).toBeNull();
    expect(filledTypes({})).toBeNull();
    expect(filledTypes(undefined)).toBeNull();
  });
});
