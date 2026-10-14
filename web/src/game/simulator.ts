import type { BattleReport, ReportItem } from '../api/queries/reports';
import { tierOfEffect } from './banners';
import { unitTypes, type BannerType, type BuildingType, type UnitType } from './gameData';
import { withoutZeros, type UnitMap } from './troops';
import { highestLevels, levelsForRequest, toLevelMap, type UnitLevelMap } from './unitLevels';

/**
 * The attack simulator's form (02-game-rules §20, M23) – pure. Everything is typed in or taken from
 * the player's own village or reports; the server runs the real battle code on it.
 */
export interface SimulatorForm {
  attacker: UnitMap;
  defender: UnitMap;
  /** The research level of each unit type per side (M30); only matters in a world with unit levels. */
  attackerLevels: UnitLevelMap;
  defenderLevels: UnitLevelMap;
  /** The wall level before the battle. */
  wall: number;
  /** What the catapults aim at; the Town Hall like in a real attack. */
  catapultTarget: BuildingType;
  /** That building's level (the wall field counts when the wall is the target). */
  catapultTargetLevel: number;
  /** The village's loyalty before the battle; only matters with nobles. */
  loyalty: number;
  /** M32c: the War banner of the attacking village, at the tier it works at there; 0 = none. */
  warTier: number;
  /** The tier of the Bulwark banner in the target village; 0 = none. The hall caps it. */
  bulwarkTier: number;
  /** The target's Banner Hall: caps the Bulwark and the Oath, makes theft harder. */
  bannerHall: number;
  /** D4: the Plunder banner of the attacking village, at the tier it works at there; 0 = none. */
  plunderTier: number;
  /** The tier of the Oath banner in the target village; 0 = none. The hall caps it. */
  oathTier: number;
}

export const emptyForm: SimulatorForm = {
  attacker: {},
  defender: {},
  attackerLevels: {},
  defenderLevels: {},
  wall: 0,
  catapultTarget: 'HEADQUARTERS',
  catapultTargetLevel: 0,
  loyalty: 100,
  warTier: 0,
  bulwarkTier: 0,
  bannerHall: 0,
  plunderTier: 0,
  oathTier: 0,
};

/** The banner fields of the form; a village flies one banner, so each side has two that exclude each other. */
export type BannerTierField = 'warTier' | 'plunderTier' | 'bulwarkTier' | 'oathTier';

const otherBanner: Record<BannerTierField, BannerTierField> = {
  warTier: 'plunderTier',
  plunderTier: 'warTier',
  bulwarkTier: 'oathTier',
  oathTier: 'bulwarkTier',
};

/**
 * The form with one banner field set: a tier above 0 clears the side's other banner, as a village flies
 * only one (the server refuses War with Plunder and Bulwark with Oath).
 */
export function withBannerTier(form: SimulatorForm, field: BannerTierField, tier: number): SimulatorForm {
  return tier > 0 ? { ...form, [field]: tier, [otherBanner[field]]: 0 } : { ...form, [field]: tier };
}

/** The highest Banner Hall level and banner tier the simulator offers. */
export const MAX_BANNER_LEVEL = 3;

/** Per unit type, the same cap as the server's request (SimulatorRequest.MAX_UNITS). */
export const MAX_SIMULATED_UNITS = 1_000_000;
/** Above every building's maximum level; the server checks the exact one. */
export const MAX_SIMULATED_LEVEL = 30;

/** Each building's highest level, from GET …/buildings (game data: the same in every village). */
export function buildingMaxLevels(
  buildings: readonly { building: BuildingType; maxLevel: number }[] | undefined,
): Partial<Record<BuildingType, number>> {
  return Object.fromEntries((buildings ?? []).map(({ building, maxLevel }) => [building, maxLevel]));
}

/**
 * The highest level a level field takes for a building: its real maximum once the building list is
 * there (a higher one is refused with VALIDATION), MAX_SIMULATED_LEVEL until then; never more than
 * the simulator's own cap for the Banner Hall.
 */
export function simulatedMaxLevel(maxLevels: Partial<Record<BuildingType, number>>, type: BuildingType): number {
  const cap = type === 'BANNER_HALL' ? MAX_BANNER_LEVEL : MAX_SIMULATED_LEVEL;
  return Math.min(maxLevels[type] ?? cap, cap);
}

/** Another catapult target: the level typed for the previous one says nothing about this one. */
export function withCatapultTarget(form: SimulatorForm, catapultTarget: BuildingType): SimulatorForm {
  return catapultTarget === form.catapultTarget ? form : { ...form, catapultTarget, catapultTargetLevel: 0 };
}

/** The request body of POST /api/simulator. */
export interface SimulatorBody {
  attacker: Record<string, number>;
  defender: Record<string, number>;
  attackerLevels: Record<string, number> | null;
  defenderLevels: Record<string, number> | null;
  wall: number;
  catapultTarget: BuildingType | null;
  catapultTargetLevel: number | null;
  loyalty: number | null;
  warTier: number | null;
  bulwarkTier: number | null;
  bannerHall: number | null;
  plunderTier: number | null;
  oathTier: number | null;
}

export function hasUnit(units: UnitMap, type: UnitType): boolean {
  return (units[type] ?? 0) > 0;
}

/** The request for a form: only what the battle uses is sent, the rest stays null. */
export function toRequest(form: SimulatorForm): SimulatorBody {
  const catapults = hasUnit(form.attacker, 'CATAPULT');
  return {
    attacker: withoutZeros(form.attacker) as Record<string, number>,
    defender: withoutZeros(form.defender) as Record<string, number>,
    // the server ignores them in a world without unit levels
    attackerLevels: levelsForRequest(form.attackerLevels),
    defenderLevels: levelsForRequest(form.defenderLevels),
    wall: form.wall,
    catapultTarget: catapults ? form.catapultTarget : null,
    // the wall's level is the wall field
    catapultTargetLevel: catapults && form.catapultTarget !== 'WALL' ? form.catapultTargetLevel : null,
    loyalty: hasUnit(form.attacker, 'NOBLE') ? form.loyalty : null,
    // the server ignores banners in a world that has them off
    warTier: form.warTier > 0 ? form.warTier : null,
    bulwarkTier: form.bulwarkTier > 0 ? form.bulwarkTier : null,
    bannerHall: form.bannerHall > 0 ? form.bannerHall : null,
    plunderTier: form.plunderTier > 0 ? form.plunderTier : null,
    oathTier: form.oathTier > 0 ? form.oathTier : null,
  };
}

/** Whether the attack can be simulated: at least one attacking unit. */
export function canSimulate(form: SimulatorForm): boolean {
  return unitTypes.some((type) => hasUnit(form.attacker, type));
}

/** Server unit maps are keyed by plain strings; keep the unit types only. */
export function toUnitMap(units: Record<string, number>): UnitMap {
  return Object.fromEntries(unitTypes.flatMap((type) => (units[type] === undefined ? [] : [[type, units[type]]])));
}

/** A level from a server map keyed by plain strings. */
function levelOf(levels: Record<string, number> | null | undefined, type: BuildingType): number | undefined {
  return levels?.[type];
}

/** Only the types the picker can show are battle reports with numbers (not support, trade, conquest). */
export function isSimulatable(item: Pick<ReportItem, 'kind'>): boolean {
  return item.kind === 'ATTACK' || item.kind === 'DEFENSE' || item.kind === 'SCOUT';
}

/** How many of the newest reports the picker looks through (the API's largest page)... */
export const SIMULATOR_REPORT_LOOKBACK = 100;
/** ...and how many battle reports it offers of them. */
export const SIMULATOR_REPORT_CHOICES = 25;

/**
 * The newest battle reports of a list (newest first) for the picker. The list mixes in trade and
 * support reports, so the picker looks through more than it shows: a trader's newest 25 reports
 * may hold no battle at all.
 */
export function simulatableReports<T extends Pick<ReportItem, 'kind'>>(items: readonly T[]): T[] {
  return items.filter(isSimulatable).slice(0, SIMULATOR_REPORT_CHOICES);
}

/**
 * What a battle report fills into the form, as it was before that battle. Parts the report doesn't
 * know stay out, so the rest of the form is kept:
 * - the attacker only for a real attack (scouts-only reports are for the defender's side: a spy
 *   report is meant to be combined with the army the player types in or takes from his village),
 * - the defenders, the wall and the catapult target only if someone came back to tell (attacker's
 *   copy) – the wall also from a successful scouting, which sees the real building levels,
 * - the unit levels the units had in that battle (M30; the defender's are the highest of its stacks,
 *   as the simulator knows one level per unit type),
 * - the loyalty before that battle, like everything else (the "after" of a conquering battle is 0,
 *   which would make any noble conquer),
 * - the banners (M32c): the War banner the attack took along, and the target's Bulwark and Banner
 *   Hall from a successful scouting (the hall from the scouted buildings); without a scouting, the
 *   Bulwark the defenders' copy names, in a hall just big enough for it. Turning the report's
 *   percent back into a tier needs the effect table (`effects`); without it they stay out. The Oath
 *   comes from a successful scouting only; a report never names the Plunder banner, so a War banner
 *   it names clears the Plunder field (one banner per village).
 */
export function reportToForm(
  battle: BattleReport,
  effects?: Partial<Record<BannerType, readonly number[]>>,
): Partial<SimulatorForm> {
  const form: Partial<SimulatorForm> = {};
  if (battle.outcome !== 'SCOUTS_ONLY') {
    form.attacker = toUnitMap(battle.attackerUnits);
    form.attackerLevels = toLevelMap(battle.attackerLevels);
  }
  const side = battle.defenderSide;
  if (side !== null) {
    form.defender = toUnitMap(side.units);
    // the defenders' copy has the levels per stack, the attacker's (upgraded scouts, M31) the highest of each
    form.defenderLevels = highestLevels([
      ...side.stacks.map((stack) => toLevelMap(stack.levels)),
      toLevelMap(side.levels),
    ]);
    form.wall = levelOf(battle.scouting?.buildings, 'WALL') ?? side.wallBefore;
  }
  if (battle.catapultTarget !== null && battle.outcome !== 'SCOUTS_ONLY') {
    form.catapultTarget = battle.catapultTarget;
    const scouted = levelOf(battle.scouting?.buildings, battle.catapultTarget);
    const known = side?.catapultLevelBefore ?? undefined;
    form.catapultTargetLevel = scouted ?? known ?? 0;
  } else if (battle.scouting?.buildings) {
    // a spy report: the Town Hall level is the default target's
    form.catapultTargetLevel = levelOf(battle.scouting.buildings, 'HEADQUARTERS') ?? 0;
  }
  if (battle.loyalty !== null) {
    form.loyalty = battle.loyalty.before;
  }
  if (battle.outcome !== 'SCOUTS_ONLY' && effects !== undefined) {
    form.warTier = tierOfEffect(effects, 'WAR', battle.banners?.attackBonus);
    if (form.warTier > 0) {
      form.plunderTier = 0;
    }
  }
  const scouted = battle.scouting;
  if (scouted?.succeeded === true && scouted.buildings) {
    form.bannerHall = levelOf(scouted.buildings, 'BANNER_HALL') ?? 0;
    form.bulwarkTier = scouted.banner?.type === 'BULWARK' ? scouted.banner.tier : 0;
    form.oathTier = scouted.banner?.type === 'OATH' ? scouted.banner.tier : 0;
  } else if (effects !== undefined && battle.banners?.defenceBonus != null) {
    const tier = tierOfEffect(effects, 'BULWARK', battle.banners.defenceBonus);
    form.bulwarkTier = tier;
    form.bannerHall = tier;
    if (tier > 0) {
      form.oathTier = 0;
    }
  }
  return form;
}

/** The units of two stacks added: the village's own units plus the supports standing in it. */
export function sumUnits(...stacks: readonly UnitMap[]): UnitMap {
  const total: UnitMap = {};
  for (const stack of stacks) {
    for (const type of unitTypes) {
      const count = stack[type] ?? 0;
      if (count > 0) {
        total[type] = (total[type] ?? 0) + count;
      }
    }
  }
  return total;
}
